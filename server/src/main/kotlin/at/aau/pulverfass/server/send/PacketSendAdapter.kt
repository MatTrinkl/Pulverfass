package at.aau.pulverfass.server.send

import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.codec.PacketCodec
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.NetworkMessageSerializer
import org.slf4j.LoggerFactory

/**
 * Technische Sendeschicht für serverseitige Netzwerkpakete.
 *
 * Die Klasse übernimmt ausschließlich das Verpacken in das Wire-Format und das
 * Ausliefern über den WebSocket-Transport. Fachliche Response-Entscheidungen
 * gehören nicht in diese Schicht.
 */
class PacketSendAdapter(
    private val transport: ServerWebSocketTransport,
) {
    private val logger = LoggerFactory.getLogger(PacketSendAdapter::class.java)

    /**
     * Verpackt ein [SerializedPacket] in Wire-Bytes und sendet diese an die
     * angegebene Verbindung.
     *
     * @throws IllegalArgumentException wenn die Verbindung unbekannt ist oder
     * das Paket technisch ungültig ist
     */
    suspend fun send(
        connectionId: ConnectionId,
        packet: SerializedPacket,
    ) {
        val bytes = PacketCodec.pack(packet)
        logger.debug(
            "Sending serialized packet to connection {} ({} bytes)",
            connectionId.value,
            bytes.size,
        )
        transport.send(connectionId, bytes)
    }

    /**
     * Hilfsmethode für den technischen Weg von Header plus bereits serialisiertem
     * Payload zu einem gesendeten Binary Frame.
     */
    suspend fun send(
        connectionId: ConnectionId,
        header: MessageHeader,
        payloadBytes: ByteArray,
    ) {
        send(connectionId, createPacket(header, payloadBytes))
    }

    /**
     * Erstellt aus Header und bereits serialisiertem Payload ein technisches
     * [SerializedPacket].
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
