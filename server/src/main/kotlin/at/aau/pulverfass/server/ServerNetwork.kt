package at.aau.pulverfass.server

import at.aau.pulverfass.server.connection.ConnectionManager
import at.aau.pulverfass.server.receive.PacketReceiver
import at.aau.pulverfass.server.send.PacketSender
import at.aau.pulverfass.server.session.SessionManager
import at.aau.pulverfass.server.session.SessionReconnectContext
import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.codec.MessageCodec
import at.aau.pulverfass.shared.network.exception.NetworkException
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory

/**
 * Serverseitige Komposition der technischen Netzwerkschichten.
 *
 * Nach außen stellt die Klasse ausschließlich die High-Level-Schnittstelle
 * [Network] bereit. Eingehende und ausgehende Nachrichten werden deshalb nur als
 * [NetworkMessagePayload] exponiert, während Transport, Framing und Dekodierung
 * intern gekapselt bleiben.
 */
class ServerNetwork(
    internal val transport: ServerWebSocketTransport = ServerWebSocketTransport(),
    internal val packetReceiver: PacketReceiver = PacketReceiver(),
    internal val connectionManager: ConnectionManager = transport.connectionManager,
    internal val sessionManager: SessionManager = SessionManager(),
) : Network<ConnectionId> {
    init {
        require(connectionManager === transport.connectionManager) {
            "ServerNetwork erwartet denselben ConnectionManager wie der Transport."
        }
    }

    private val logger = LoggerFactory.getLogger(ServerNetwork::class.java)
    private val sender: PacketSender = PacketSender(connectionManager)
    private val _events = MutableSharedFlow<Network.Event<ConnectionId>>(extraBufferCapacity = 64)
    private var reconnectContextProvider: (SessionToken) -> SessionReconnectContext? = { null }
    private var onSessionRemoved: (SessionToken) -> Unit = {}

    /**
     * High-Level-Eventstrom des Servers.
     *
     * Er enthält nur Verbindungsereignisse, dekodierte Payloads und technische
     * Fehler, aber keine Low-Level-WebSocket-Frames.
     */
    override val events: SharedFlow<Network.Event<ConnectionId>> = _events.asSharedFlow()

    /**
     * Installiert optionale Hooks für Reconnect-Kontext und Session-Cleanup.
     */
    fun installReconnectHooks(
        reconnectContextProvider: (SessionToken) -> SessionReconnectContext? = { null },
        onSessionRemoved: (SessionToken) -> Unit = {},
    ) {
        this.reconnectContextProvider = reconnectContextProvider
        this.onSessionRemoved = onSessionRemoved
    }

    /**
     * Registriert eine neue WebSocket-Verbindung im Transport und emittiert das
     * zugehörige High-Level-Connect-Event.
     *
     * Diese Methode ist Teil des internen WebSocket-Lifecycle-Pfads und nicht
     * für fachliche Integrationen gedacht.
     */
    internal suspend fun onConnected(
        connectionId: ConnectionId,
        session: DefaultWebSocketServerSession,
    ) {
        transport.onConnected(connectionId, session)
        val createdSession = sessionManager.createSession(connectionId)
        sender.send(
            connectionId = connectionId,
            bytes = MessageCodec.encode(ConnectionResponse(createdSession.sessionToken)),
        )
        _events.emit(Network.Event.Connected(connectionId))
    }

    /**
     * Verarbeitet ein empfangenes Binary Frame ende-zu-ende:
     * Transportevent, Header-Dekodierung und abschließendes Payload-Decoding.
     *
     * Fachlogik wird hier bewusst nicht ausgeführt. Erfolgreiche Payloads werden
     * ausschließlich als [Network.Event.MessageReceived] weitergereicht.
     */
    internal suspend fun onBinaryMessage(
        connectionId: ConnectionId,
        bytes: ByteArray,
    ) {
        transport.onBinaryMessage(connectionId, bytes)

        val receivedPacket = packetReceiver.decode(connectionId, bytes)
        if (receivedPacket == null) {
            val cause =
                IllegalArgumentException(
                    "Failed to decode packet for connection ${connectionId.value}",
                )
            logger.warn(
                "Failed to decode packet for connection {}",
                connectionId.value,
                cause,
            )
            _events.emit(Network.Event.Error(connectionId, cause))
            return
        }

        try {
            val payload = MessageCodec.decodePayload(receivedPacket)
            if (payload is ReconnectRequest) {
                handleReconnect(connectionId, payload)
                return
            }
            _events.emit(Network.Event.MessageReceived(connectionId, payload))
        } catch (cause: NetworkException) {
            logger.warn(
                "Failed to deserialize payload {} for connection {}",
                receivedPacket.header.type,
                connectionId.value,
                cause,
            )
            _events.emit(Network.Event.Error(connectionId, cause))
        }
    }

    /**
     * Entfernt eine beendete Verbindung aus dem Transport und emittiert das
     * entsprechende High-Level-Disconnect-Event.
     */
    internal suspend fun onDisconnected(
        connectionId: ConnectionId,
        reason: String?,
    ) {
        sessionManager.detachConnection(connectionId)
        transport.onDisconnected(connectionId, reason)
        _events.emit(Network.Event.Disconnected(connectionId, reason))
    }

    /**
     * Meldet einen technischen Fehler aus dem WebSocket- oder Transportpfad an
     * die High-Level-API weiter.
     */
    internal suspend fun onError(
        connectionId: ConnectionId?,
        cause: Throwable,
    ) {
        transport.onError(connectionId, cause)
        _events.emit(Network.Event.Error(connectionId, cause))
    }

    /**
     * Sendet eine fachliche Payload an eine bestehende Verbindung.
     *
     * Die Payload wird intern über [MessageCodec] in das Protokollformat
     * serialisiert und anschließend als Binary Frame verschickt.
     */
    override suspend fun send(
        connectionId: ConnectionId,
        payload: NetworkMessagePayload,
    ) {
        sender.send(connectionId, MessageCodec.encode(payload))
    }

    private suspend fun handleReconnect(
        connectionId: ConnectionId,
        payload: ReconnectRequest,
    ) {
        val reconnectError = sessionManager.reconnectErrorFor(payload.sessionToken)
        if (reconnectError != null) {
            sendReconnectResponse(
                connectionId = connectionId,
                payload =
                    ReconnectResponse(
                        success = false,
                        errorCode = reconnectError,
                    ),
            )
            return
        }

        val currentSession = sessionManager.requireByConnectionId(connectionId)
        val previousConnectionId = sessionManager.getByToken(payload.sessionToken)?.connectionId

        if (currentSession.sessionToken == payload.sessionToken) {
            sendReconnectResponse(
                connectionId = connectionId,
                payload = createReconnectSuccessResponse(payload.sessionToken),
            )
            return
        }

        val removedSession = sessionManager.removeByConnectionId(connectionId)
        removedSession?.let { removed -> onSessionRemoved(removed.sessionToken) }
        sessionManager.bindExisting(payload.sessionToken, connectionId)

        if (previousConnectionId != null && previousConnectionId != connectionId) {
            closeConnectionForReconnect(previousConnectionId)
        }

        sendReconnectResponse(
            connectionId = connectionId,
            payload = createReconnectSuccessResponse(payload.sessionToken),
        )
    }

    private suspend fun closeConnectionForReconnect(connectionId: ConnectionId) {
        runCatching {
            connectionManager.close(
                connectionId = connectionId,
                reason = RECONNECT_REPLACED_REASON,
            )
        }.onFailure { cause ->
            logger.warn(
                "Failed to close superseded connection {} during reconnect",
                connectionId.value,
                cause,
            )
        }
    }

    private suspend fun sendReconnectResponse(
        connectionId: ConnectionId,
        payload: ReconnectResponse,
    ) {
        sender.send(
            connectionId = connectionId,
            bytes = MessageCodec.encode(payload),
        )
    }

    private fun createReconnectSuccessResponse(sessionToken: SessionToken): ReconnectResponse {
        val context = reconnectContextProvider(sessionToken)
        return ReconnectResponse(
            success = true,
            playerId = context?.playerId,
            lobbyCode = context?.lobbyCode,
            playerDisplayName = context?.playerDisplayName,
        )
    }

    private companion object {
        const val RECONNECT_REPLACED_REASON = "Connection replaced by reconnect."
    }
}
