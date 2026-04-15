package at.aau.pulverfass.app.network

import at.aau.pulverfass.app.network.receive.PacketReceiver
import at.aau.pulverfass.app.network.send.PacketSender
import at.aau.pulverfass.app.network.transport.AndroidWebSocketTransport
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Android-seitige Komposition der technischen Netzwerkschichten.
 *
 * Die Klasse bündelt WebSocket-Transport, Header-Dekodierung eingehender
 * Pakete und den technischen Sendepfad für ausgehende Pakete.
 */
class ClientNetwork(
    scope: CoroutineScope,
    val transport: AndroidWebSocketTransport = AndroidWebSocketTransport(scope),
    val packetReceiver: PacketReceiver = PacketReceiver(),
) {
    private val sender: PacketSender = PacketSender(transport)

    init {
        scope.launch {
            transport.events.collect { event ->
                packetReceiver.onTransportEvent(event)
            }
        }
    }

    suspend fun connect(serverUrl: String) {
        transport.connect(serverUrl)
    }

    suspend fun disconnect(reason: String? = null) {
        transport.disconnect(reason)
    }

    suspend fun sendPayload(payload: NetworkMessagePayload) {
        val bytes = MessageCodec.encode(payload)
        sender.send(CLIENT_CONNECTION_ID, bytes)
    }

    fun close() {
        transport.close()
    }
}
