package at.aau.pulverfass.server

import at.aau.pulverfass.server.receive.PacketReceiver
import at.aau.pulverfass.server.send.PacketSender
import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.codec.MessageCodec
import at.aau.pulverfass.shared.network.exception.NetworkException
import at.aau.pulverfass.shared.network.message.NetworkMessagePayload
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
) : Network<ConnectionId> {
    private val logger = LoggerFactory.getLogger(ServerNetwork::class.java)
    private val sender: PacketSender = PacketSender(transport)
    private val _events = MutableSharedFlow<Network.Event<ConnectionId>>(extraBufferCapacity = 64)

    /**
     * High-Level-Eventstrom des Servers.
     *
     * Er enthält nur Verbindungsereignisse, dekodierte Payloads und technische
     * Fehler, aber keine Low-Level-WebSocket-Frames.
     */
    override val events: SharedFlow<Network.Event<ConnectionId>> = _events.asSharedFlow()

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
            val cause = IllegalArgumentException(
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
            val payload = MessageCodec.decodePayload(bytes)
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
}
