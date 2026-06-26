package at.aau.pulverfass.client.network.send

import at.aau.pulverfass.client.network.CLIENT_CONNECTION_ID
import at.aau.pulverfass.client.network.transport.WebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Android-seitiger Raw-Byte-Sender für ausgehende Netzwerkpakete.
 */
class PacketSender(
    private val transport: WebSocketTransport,
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
