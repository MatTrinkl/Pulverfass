package at.aau.pulverfass.server.connection

import at.aau.pulverfass.shared.ids.ConnectionId
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame

/**
 * WebSocket-basierte technische Verbindung.
 *
 * Die Klasse kapselt die Ktor-Session hinter der transportneutralen
 * [Connection]-Schnittstelle.
 */
class WebSocketConnection(
    override val connectionId: ConnectionId,
    private val session: DefaultWebSocketServerSession,
) : Connection {
    override suspend fun send(bytes: ByteArray) {
        session.send(Frame.Binary(fin = true, data = bytes.copyOf()))
    }
}
