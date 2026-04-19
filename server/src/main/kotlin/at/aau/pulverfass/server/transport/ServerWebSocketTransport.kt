package at.aau.pulverfass.server.transport

import at.aau.pulverfass.server.connection.ConnectionManager
import at.aau.pulverfass.server.connection.WebSocketConnection
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.transport.BinaryMessageReceived
import at.aau.pulverfass.shared.network.transport.Connected
import at.aau.pulverfass.shared.network.transport.Disconnected
import at.aau.pulverfass.shared.network.transport.TransportError
import at.aau.pulverfass.shared.network.transport.TransportEvent
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory

/**
 * Technische Transport-Schicht für serverseitige WebSocket-Verbindungen.
 *
 * Die Klasse emittiert reine Connection- und Binary-Events, während die
 * Registrierung aktiver Verbindungen an den [ConnectionManager] delegiert wird.
 * Fachliche Auswertung oder Dekodierung gehört ausdrücklich nicht in diese Schicht.
 */
class ServerWebSocketTransport(
    val connectionManager: ConnectionManager = ConnectionManager(),
) {
    private val logger = LoggerFactory.getLogger(ServerWebSocketTransport::class.java)
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
        connectionManager.register(WebSocketConnection(connectionId, session))
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
        connectionManager.remove(connectionId)
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
     * @throws at.aau.pulverfass.server.ids.ConnectionNotFoundException wenn die
     * Verbindung nicht mehr registriert ist
     */
    suspend fun send(
        connectionId: ConnectionId,
        bytes: ByteArray,
    ) {
        val connection = connectionManager.require(connectionId)

        logger.debug(
            "Sending binary websocket frame to connection {} ({} bytes)",
            connectionId.value,
            bytes.size,
        )

        try {
            connection.send(bytes)
        } catch (cause: Throwable) {
            onError(connectionId, cause)
            throw cause
        }
    }
}
