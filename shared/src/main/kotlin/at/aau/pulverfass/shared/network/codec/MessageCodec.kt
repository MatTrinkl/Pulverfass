package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.InvalidSerializedPacketException
import at.aau.pulverfass.shared.network.message.NetworkMessagePayload
import at.aau.pulverfass.shared.network.message.NetworkMessageSerializer
import kotlinx.serialization.KSerializer

/**
 * Zentraler Einstiegspunkt für die Kodierung und Dekodierung von Netzwerk-Nachrichten.
 *
 * Diese Klasse setzt die beiden bereits getrennten Ebenen des Protokolls zusammen:
 * fachliche Serialisierung von Header und Payload via [NetworkMessageSerializer]
 * sowie das Transport-Framing via [PacketCodec].
 *
 * Ablauf beim Encoden:
 * 1. [NetworkPacket] in Header- und Payload-Bytes serialisieren
 * 2. Die serialisierten Teile in ein transportierbares Byte-Format packen
 *
 * Ablauf beim Decoden:
 * 1. Transport-Bytes wieder in Header- und Payload-Bytes zerlegen
 * 2. Header und Payload anhand des Header-Typs in Objekte deserialisieren
 *
 * Dadurch können Aufrufer das Protokoll über diese eine Klasse verwenden,
 * ohne selbst zwischen Objekt-, Serialisierungs- und Framing-Ebene wechseln zu müssen.
 */
internal object MessageCodec {
    /**
     * Kodiert ein fachliches [NetworkPacket] in das Byte-Format, das über den Transport
     * versendet werden kann.
     *
     * @param packet das zu kodierende Netzwerkpaket
     * @param serializer Serializer für den konkreten Payload-Typ des Pakets
     * @return das vollständig kodierte ByteArray inklusive Framing
     * @throws at.aau.pulverfass.shared.network.NetworkSerializationException wenn Header oder Payload
     * nicht serialisiert werden können
     * @throws at.aau.pulverfass.shared.network.UnsupportedPayloadClassException wenn der Payload-Typ
     * nicht im Protokoll registriert ist
     * @throws at.aau.pulverfass.shared.network.PayloadTypeMismatchException wenn Header-Typ und
     * Payload-Typ nicht zusammenpassen
     * @throws InvalidSerializedPacketException wenn das serialisierte Paket keine gültige Basisstruktur hat
     */
    fun <T : NetworkMessagePayload> encode(
        packet: NetworkPacket<T>,
        serializer: KSerializer<T>,
    ): ByteArray =
        try {
            PacketCodec.pack(serialize(packet, serializer))
        } catch (exception: PacketCodecException) {
            throw InvalidSerializedPacketException(exception.message ?: "Invalid serialized packet")
        }

    /**
     * Dekodiert ein empfangenes ByteArray in ein fachliches [NetworkPacket].
     *
     * Das ByteArray muss dem durch [PacketCodec] definierten Framing entsprechen.
     * Der Payload-Typ wird anhand des im Header enthaltenen [at.aau.pulverfass.shared.network.message.MessageType]s
     * bestimmt.
     *
     * @param bytes das empfangene ByteArray inklusive Framing
     * @return das deserialisierte Netzwerkpaket mit Header und Payload
     * @throws InvalidSerializedPacketException wenn das ByteArray kein gültiges transportiertes Paket beschreibt
     * @throws at.aau.pulverfass.shared.network.NetworkSerializationException wenn Header oder Payload
     * nicht deserialisiert werden können
     * @throws at.aau.pulverfass.shared.network.UnsupportedPayloadTypeException wenn für den Header-Typ
     * kein Payload-Mapping hinterlegt ist
     */
    fun decode(bytes: ByteArray): NetworkPacket<NetworkMessagePayload> = deserialize(unpack(bytes))

    /**
     * Serialisiert ein [NetworkPacket] in seine Header- und Payload-Bestandteile, ohne
     * bereits das Transport-Framing anzuwenden.
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
     * Deserialisiert ein bereits entpacktes [SerializedPacket] in ein fachliches [NetworkPacket].
     */
    internal fun deserialize(packet: SerializedPacket): NetworkPacket<NetworkMessagePayload> {
        val header = NetworkMessageSerializer.deserializeHeader(packet.headerBytes)
        val payload = NetworkMessageSerializer.deserializePayload(header.type, packet.payloadBytes)

        return NetworkPacket(
            header = header,
            payload = payload,
        )
    }

    private fun unpack(bytes: ByteArray): SerializedPacket =
        try {
            PacketCodec.unpack(bytes)
        } catch (exception: PacketCodecException) {
            throw InvalidSerializedPacketException(exception.message ?: "Invalid serialized packet")
        }
}
