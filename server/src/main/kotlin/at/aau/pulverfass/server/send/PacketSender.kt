package at.aau.pulverfass.server.send

import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Serverseitiger Raw-Byte-Sender fuer ausgehende Netzwerkpakete.
 */
class PacketSender(
    private val transport: ServerWebSocketTransport,
) {
    suspend fun send(
        connectionId: ConnectionId,
        bytes: ByteArray,
    ) {
        transport.send(connectionId, bytes)
    }
}
