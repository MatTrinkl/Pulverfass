package at.aau.pulverfass.app.network

import at.aau.pulverfass.app.network.receive.PacketReceiver
import at.aau.pulverfass.app.network.send.PacketSender
import at.aau.pulverfass.app.network.transport.AndroidWebSocketTransport
import at.aau.pulverfass.shared.network.message.LoginRequest
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.message.NetworkMessageSerializer
import at.aau.pulverfass.shared.network.send.PacketSendAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Android-seitige Komposition der technischen Netzwerkschichten.
 *
 * Die Klasse buendelt WebSocket-Transport, Header-Dekodierung eingehender
 * Pakete und den technischen Sendepfad fuer ausgehende Pakete.
 */
class ClientNetwork(
    scope: CoroutineScope,
    val transport: AndroidWebSocketTransport = AndroidWebSocketTransport(scope),
    val packetReceiver: PacketReceiver = PacketReceiver(),
) {
    private val sender: PacketSender = PacketSender(transport)
    private val packetSender: PacketSendAdapter = PacketSendAdapter(sender::send)

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

    /**
     * Sendet den technischen Login-Request mit dem in Shared definierten
     * Payload-Vertrag.
     */
    suspend fun sendLoginRequest(
        username: String,
        password: String,
    ) {
        val payload =
            LoginRequest(
                username = username,
                password = password,
            )
        val payloadBytes =
            NetworkMessageSerializer.serializePayload(
                serializer = LoginRequest.serializer(),
                payload = payload,
            )

        packetSender.send(
            connectionId = CLIENT_CONNECTION_ID,
            header = MessageHeader(MessageType.LOGIN_REQUEST),
            payloadBytes = payloadBytes,
        )
    }

    /**
     * Sendet eine technische Lobby-Nachricht als JSON-String.
     *
     * Diese Methode ist bewusst als Vorlage gehalten, bis fuer alle
     * Lobby-MessageTypes konkrete Shared-Payload-Klassen existieren.
     */
    suspend fun sendJsonMessage(
        messageType: MessageType,
        payloadJson: String,
    ) {
        packetSender.send(
            connectionId = CLIENT_CONNECTION_ID,
            header = MessageHeader(messageType),
            payloadBytes = payloadJson.encodeToByteArray(),
        )
    }

    fun close() {
        transport.close()
    }
}
