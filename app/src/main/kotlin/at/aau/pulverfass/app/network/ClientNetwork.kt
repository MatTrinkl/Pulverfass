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

    /**
     * Baut eine technische WebSocket-Verbindung zum Server auf.
     *
     * Ein erneuter Aufruf delegiert an den Transport, der eine bestehende
     * Verbindung vor einem Reconnect selbst sauber trennt.
     */
    suspend fun connect(serverUrl: String) {
        transport.connect(serverUrl)
    }

    /**
     * Trennt die aktuelle Verbindung.
     *
     * Der optionale [reason] wird nur an den Transport weitergereicht und
     * beeinflusst keine fachliche Client-State-Maschine.
     */
    suspend fun disconnect(reason: String? = null) {
        transport.disconnect(reason)
    }

    /**
     * Serialisiert eine fachliche Payload in das binäre Protokollformat und
     * sendet sie über die einzige vom Android-Client unterstützte Verbindung.
     *
     * @throws IllegalStateException wenn der zugrunde liegende Transport keine
     * aktive Verbindung hält
     */
    suspend fun sendPayload(payload: NetworkMessagePayload) {
        val bytes = MessageCodec.encode(payload)
        sender.send(CLIENT_CONNECTION_ID, bytes)
    }

    /**
     * Gibt Transportressourcen frei und beendet die Lebensdauer dieses
     * Netzwerkstacks.
     */
    fun close() {
        transport.close()
    }
}
