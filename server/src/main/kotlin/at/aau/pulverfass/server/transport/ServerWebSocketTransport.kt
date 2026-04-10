package at.aau.pulverfass.server.transport

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.transport.BinaryMessageReceived
import at.aau.pulverfass.shared.network.transport.Connected
import at.aau.pulverfass.shared.network.transport.Disconnected
import at.aau.pulverfass.shared.network.transport.TransportError
import at.aau.pulverfass.shared.network.transport.TransportEvent
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Technische Transport-Schicht für serverseitige WebSocket-Verbindungen.
 *
 * Die Klasse verwaltet aktive Sessions anhand ihrer [ConnectionId], emittiert
 * reine Connection- und Binary-Events und erlaubt das Senden roher ByteArrays
 * an bekannte Verbindungen. Fachliche Auswertung oder Dekodierung gehört
 * ausdrücklich nicht in diese Schicht.
 */
class ServerWebSocketTransport {
    private val logger = LoggerFactory.getLogger(ServerWebSocketTransport::class.java)
    private val sessions = ConcurrentHashMap<ConnectionId, DefaultWebSocketServerSession>()
    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)

    /**
     * Strom aller technischen Transport-Events dieser Serverinstanz.
     */
    val events: SharedFlow<TransportEvent> = _events.asSharedFlow()

    /**
     * Registriert eine neu aufgebaute Verbindung und emittiert ein Connect-Event.
     */
    suspend fun onConnected(
        connectionId: ConnectionId,
        session: DefaultWebSocketServerSession,
    ) {
        sessions[connectionId] = session
        logger.info("Accepted websocket connection {}", connectionId.value)
        _events.emit(Connected(connectionId))
    }

    /**
     * Emittiert ein Event für einen empfangenen Binaryframe mit rohen Bytes.
     */
    suspend fun onBinaryMessage(
        connectionId: ConnectionId,
        bytes: ByteArray,
    ) {
        logger.debug(
            "Received binary websocket frame for connection {} ({} bytes)",
            connectionId.value,
            bytes.size,
        )
        _events.emit(BinaryMessageReceived(connectionId, bytes))
    }

    /**
     * Entfernt eine Verbindung aus der Registry und emittiert das Disconnect-Event.
     */
    suspend fun onDisconnected(
        connectionId: ConnectionId,
        reason: String?,
    ) {
        sessions.remove(connectionId)
        logger.info("Closed websocket connection {} with reason '{}'", connectionId.value, reason)
        _events.emit(Disconnected(connectionId, reason))
    }

    /**
     * Emittiert ein optionales technisches Fehlerereignis auf der Transportebene.
     */
    suspend fun onError(
        connectionId: ConnectionId?,
        cause: Throwable,
    ) {
        logger.warn("Websocket transport error on connection {}", connectionId?.value, cause)
        _events.emit(TransportError(connectionId, cause))
    }

    /**
     * Sendet rohe Bytes als Binary Frame an eine bekannte Verbindung.
     *
     * @throws IllegalArgumentException wenn die Verbindung nicht mehr registriert ist
     */
    suspend fun send(
        connectionId: ConnectionId,
        bytes: ByteArray,
    ) {
        val session =
            sessions[connectionId]
                ?: throw IllegalArgumentException("Unknown connection: $connectionId")

        logger.debug(
            "Sending binary websocket frame to connection {} ({} bytes)",
            connectionId.value,
            bytes.size,
        )

        try {
            session.send(Frame.Binary(fin = true, data = bytes.copyOf()))
        } catch (cause: Throwable) {
            onError(connectionId, cause)
            throw cause
        }
    }
}
