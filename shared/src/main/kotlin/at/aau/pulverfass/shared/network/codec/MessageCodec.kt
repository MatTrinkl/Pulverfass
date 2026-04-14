package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.exception.InvalidSerializedPacketException
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.NetworkMessagePayload
import at.aau.pulverfass.shared.network.message.NetworkMessageSerializer
import at.aau.pulverfass.shared.network.message.NetworkPayloadRegistry
import kotlinx.serialization.KSerializer

/**
 * Zentraler Einstiegspunkt für die Kodierung und Dekodierung von
 * Netzwerknachrichten.
 *
 * Die Klasse verbindet die fachliche Serialisierung von Header und Payload mit
 * dem binären Framing für den Transport.
 */
object MessageCodec {
    /**
     * Kodiert eine fachliche Payload direkt in das vollständige Wire-Format.
     *
     * Der zugehörige Nachrichtentyp wird über die Payload-Registry bestimmt.
     *
     * @param payload fachliche Nutzlast
     * @return transportierbare Wire-Bytes
     * @throws InvalidSerializedPacketException wenn beim Framing eine ungültige
     * Paketstruktur entsteht
     */
    fun encode(payload: NetworkMessagePayload): ByteArray =
        PacketCodec.pack(
            SerializedPacket(
                headerBytes =
                    NetworkMessageSerializer.serializeHeader(
                        MessageHeader(NetworkPayloadRegistry.messageTypeFor(payload)),
                    ),
                payloadBytes = NetworkMessageSerializer.serializePayload(payload),
            ),
        )

    /**
     * Kodiert ein bereits zusammengesetztes [NetworkPacket].
     *
     * Diese Überladung wird nur intern für Tests und technische Zwischenschichten
     * benötigt.
     */
    internal fun <T : NetworkMessagePayload> encode(
        packet: NetworkPacket<T>,
        serializer: KSerializer<T>,
    ): ByteArray = PacketCodec.pack(serialize(packet, serializer))

    /**
     * Dekodiert transportierte Bytes direkt in eine fachliche Payload.
     *
     * @param bytes empfangene Wire-Bytes
     * @return dekodierte Payload
     * @throws InvalidSerializedPacketException wenn das Wire-Format ungültig ist
     */
    fun decodePayload(bytes: ByteArray): NetworkMessagePayload = decode(bytes).payload

    /**
     * Dekodiert transportierte Bytes intern in ein [NetworkPacket].
     */
    internal fun decode(bytes: ByteArray): NetworkPacket<NetworkMessagePayload> =
        deserialize(unpack(bytes))

    /**
     * Serialisiert ein [NetworkPacket] in Header- und Payload-Bytes, ohne das
     * Transport-Framing anzuwenden.
     */
    internal fun <T : NetworkMessagePayload> serialize(
        packet: NetworkPacket<T>,
        serializer: KSerializer<T>,
    ): SerializedPacket =
        SerializedPacket(
            headerBytes = NetworkMessageSerializer.serializeHeader(packet.header),
            payloadBytes = NetworkMessageSerializer.serializePayload(serializer, packet.payload),
        )

    /**
     * Deserialisiert ein bereits entpacktes [SerializedPacket] in ein fachliches
     * [NetworkPacket].
     */
    internal fun deserialize(packet: SerializedPacket): NetworkPacket<NetworkMessagePayload> {
        val header = NetworkMessageSerializer.deserializeHeader(packet.headerBytes)
        val payload = NetworkMessageSerializer.deserializePayload(header.type, packet.payloadBytes)

        return NetworkPacket(
            header = header,
            payload = payload,
        )
    }

    /**
     * Entpackt transportierte Bytes in ein [SerializedPacket] und übersetzt
     * Framing-Fehler in die öffentliche Protokollexception.
     */
    private fun unpack(bytes: ByteArray): SerializedPacket =
        try {
            PacketCodec.unpack(bytes)
        } catch (exception: PacketCodecException) {
            throw InvalidSerializedPacketException(exception.message.toString())
        }
}
