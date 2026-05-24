package at.aau.pulverfass.server

import at.aau.pulverfass.server.connection.ConnectionManager
import at.aau.pulverfass.server.logging.ServerLoggers
import at.aau.pulverfass.server.receive.PacketReceiver
import at.aau.pulverfass.server.send.PacketSender
import at.aau.pulverfass.server.session.PersistedReconnectSession
import at.aau.pulverfass.server.session.SessionManager
import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectErrorCode
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.codec.MessageCodec
import at.aau.pulverfass.shared.network.exception.NetworkException
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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

    private val logger = ServerLoggers.technical("ServerNetwork")
    private val sender: PacketSender = PacketSender(connectionManager)
    private val _events = MutableSharedFlow<Network.Event<ConnectionId>>(extraBufferCapacity = 64)
    private var reconnectSessionProvider: (SessionToken) -> PersistedReconnectSession? = { null }
    private var onReconnectSucceeded: (SessionToken) -> Unit = {}
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
        reconnectSessionProvider: (SessionToken) -> PersistedReconnectSession? = { null },
        onReconnectSucceeded: (SessionToken) -> Unit = {},
        onSessionRemoved: (SessionToken) -> Unit = {},
    ) {
        this.reconnectSessionProvider = reconnectSessionProvider
        this.onReconnectSucceeded = onReconnectSucceeded
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
        logger.info(
            "Connection session created connectionId={}",
            connectionId.value,
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

        val receivedPacket = packetReceiver.decodeWithoutPublishing(connectionId, bytes)
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
            logger.info(
                "Decoded payload connectionId={} messageType={} payloadType={}",
                connectionId.value,
                receivedPacket.header.type,
                payload::class.simpleName,
            )
            if (payload is ReconnectRequest) {
                handleReconnect(connectionId, payload)
                packetReceiver.publish(receivedPacket)
                return
            }
            packetReceiver.publish(receivedPacket)
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
        val detachedSession = sessionManager.detachConnection(connectionId)
        transport.onDisconnected(connectionId, reason)
        _events.emit(
            Network.Event.Disconnected(
                connectionId = connectionId,
                reason = reason,
                sessionToken = detachedSession?.sessionToken,
            ),
        )
        logger.info(
            "Connection detached connectionId={} reason={}",
            connectionId.value,
            reason,
        )
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
        logger.warn(
            "Network error connectionId={}",
            connectionId?.value,
            cause,
        )
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
        logger.info("Reconnect requested connectionId={}", connectionId.value)
        var reconnectError = sessionManager.reconnectErrorFor(payload.sessionToken)
        if (reconnectError == ReconnectErrorCode.TOKEN_INVALID) {
            val persistedSession = reconnectSessionProvider(payload.sessionToken)
            if (persistedSession != null) {
                logger.info(
                    "Reconnect session restored connectionId={} playerId={} lobbyCode={}",
                    connectionId.value,
                    persistedSession.context.playerId?.value,
                    persistedSession.context.lobbyCode?.value,
                )
                sessionManager.restoreDetachedSession(
                    sessionToken = payload.sessionToken,
                    expiresAtEpochMillis = persistedSession.expiresAtEpochMillis,
                    revokedAtEpochMillis = persistedSession.revokedAtEpochMillis,
                )
                reconnectError = sessionManager.reconnectErrorFor(payload.sessionToken)
            }
        }
        if (reconnectError != null) {
            logger.warn(
                "Reconnect rejected connectionId={} errorCode={}",
                connectionId.value,
                reconnectError,
            )
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
            onReconnectSucceeded(payload.sessionToken)
            val response = createReconnectSuccessResponse(payload.sessionToken)
            logger.info(
                "Reconnect confirmed existing session connectionId={} playerId={} lobbyCode={}",
                connectionId.value,
                response.playerId?.value,
                response.lobbyCode?.value,
            )
            sendReconnectResponse(
                connectionId = connectionId,
                payload = response,
            )
            return
        }

        val removedSession = sessionManager.removeByConnectionId(connectionId)
        removedSession?.let { removed -> onSessionRemoved(removed.sessionToken) }
        sessionManager.bindExisting(payload.sessionToken, connectionId)
        onReconnectSucceeded(payload.sessionToken)

        if (previousConnectionId != null && previousConnectionId != connectionId) {
            closeConnectionForReconnect(previousConnectionId)
        }

        val response = createReconnectSuccessResponse(payload.sessionToken)
        logger.info(
            "Reconnect rebound session connectionId={} previousConnectionId={} " +
                "playerId={} lobbyCode={}",
            connectionId.value,
            previousConnectionId?.value,
            response.playerId?.value,
            response.lobbyCode?.value,
        )
        sendReconnectResponse(
            connectionId = connectionId,
            payload = response,
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
        val context = reconnectSessionProvider(sessionToken)?.context
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
