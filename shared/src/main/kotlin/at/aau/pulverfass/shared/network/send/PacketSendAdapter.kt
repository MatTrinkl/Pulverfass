package at.aau.pulverfass.shared.network.send

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.message.codec.NetworkMessageSerializer
import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.network.codec.PacketCodec
import at.aau.pulverfass.shared.network.codec.SerializedPacket

/**
 * Interne technische Sendeschicht für Netzwerkpakete.
 *
 * Die Klasse übernimmt ausschließlich das Verpacken in das Wire-Format und das
 * Ausliefern über einen injizierten Raw-Byte-Sender.
 */
internal class PacketSendAdapter(
    private val sender: suspend (ConnectionId, ByteArray) -> Unit,
) {
    /**
     * Verpackt ein [SerializedPacket] in Wire-Bytes und sendet diese an die
     * angegebene Verbindung.
     *
     * @param connectionId Zielverbindung
     * @param packet bereits serialisiertes Paket
     */
    suspend fun send(
        connectionId: ConnectionId,
        packet: SerializedPacket,
    ) {
        val bytes = PacketCodec.pack(packet)
        sender(connectionId, bytes)
    }

    /**
     * Erstellt aus Header und Payload ein technisches Paket und sendet es.
     *
     * @param connectionId Zielverbindung
     * @param header technischer Nachrichtenkopf
     * @param payloadBytes bereits serialisierte Payload
     */
    suspend fun send(
        connectionId: ConnectionId,
        header: MessageHeader,
        payloadBytes: ByteArray,
    ) {
        send(connectionId, createPacket(header, payloadBytes))
    }

    /**
     * Erstellt aus Header und bereits serialisierter Payload ein [SerializedPacket].
     *
     * @param header technischer Nachrichtenkopf
     * @param payloadBytes bereits serialisierte Payload
     * @return technisches Paketmodell für den Wire-Versand
     */
    fun createPacket(
        header: MessageHeader,
        payloadBytes: ByteArray,
    ): SerializedPacket =
        SerializedPacket(
            headerBytes = NetworkMessageSerializer.serializeHeader(header),
            payloadBytes = payloadBytes,
        )
}
