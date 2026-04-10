package at.aau.pulverfass.app.network.send

import at.aau.pulverfass.app.network.CLIENT_CONNECTION_ID
import at.aau.pulverfass.app.network.transport.AndroidWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Android-seitiger Raw-Byte-Sender fuer ausgehende Netzwerkpakete.
 */
class PacketSender(
    private val transport: AndroidWebSocketTransport,
) {
    suspend fun send(
        connectionId: ConnectionId,
        bytes: ByteArray,
    ) {
        require(connectionId == CLIENT_CONNECTION_ID) {
            "Android client supports only one active connection"
        }
        transport.send(bytes)
    }
}
