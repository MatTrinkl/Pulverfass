package at.aau.pulverfass.server

import at.aau.pulverfass.server.receive.PacketReceiver
import at.aau.pulverfass.server.send.PacketSendAdapter
import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.transport.BinaryMessageReceived
import io.ktor.server.websocket.DefaultWebSocketServerSession

/**
 * Serverseitige Komposition der technischen Netzwerkschichten.
 *
 * Die Klasse bündelt WebSocket-Transport, Header-Dekodierung eingehender
 * Pakete und den technischen Sendepfad für ausgehende Pakete. Fachlogik wird
 * bewusst nicht eingebunden.
 */
class ServerNetwork(
    val transport: ServerWebSocketTransport = ServerWebSocketTransport(),
    val packetReceiver: PacketReceiver = PacketReceiver(),
) {
    val packetSender: PacketSendAdapter = PacketSendAdapter(transport)

    suspend fun onConnected(
        connectionId: ConnectionId,
        session: DefaultWebSocketServerSession,
    ) {
        transport.onConnected(connectionId, session)
    }

    suspend fun onBinaryMessage(
        connectionId: ConnectionId,
        bytes: ByteArray,
    ) {
        val event = BinaryMessageReceived(connectionId, bytes)

        transport.onBinaryMessage(connectionId, bytes)
        packetReceiver.onTransportEvent(event)
    }

    suspend fun onDisconnected(
        connectionId: ConnectionId,
        reason: String?,
    ) {
        transport.onDisconnected(connectionId, reason)
    }

    suspend fun onError(
        connectionId: ConnectionId?,
        cause: Throwable,
    ) {
        transport.onError(connectionId, cause)
    }
}
