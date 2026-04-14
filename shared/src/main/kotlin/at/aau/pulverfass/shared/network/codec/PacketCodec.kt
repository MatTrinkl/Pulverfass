package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.exception.NetworkException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Kodiert und dekodiert das binäre Transportformat eines [SerializedPacket]s.
 *
 * Dieser Codec ist ausschließlich für das Byte-Framing verantwortlich. Er kennt
 * keine fachlichen Nachrichtentypen und arbeitet nur mit bereits serialisierten
 * Header- und Payload-Bytes.
 *
 * Das Wire-Format lautet:
 * `[Int32 headerLength][headerBytes][payloadBytes]`
 */
object PacketCodec {
    /**
     * Baut aus einem [SerializedPacket] das transportierbare Byte-Format.
     *
     * @param packet bereits serialisiertes Paket
     * @return vollständig gepacktes ByteArray für den Transport
     */
    fun pack(packet: SerializedPacket): ByteArray {
        val headerLength = packet.headerBytes.size
        val buffer =
            ByteBuffer.allocate(Int.SIZE_BYTES + headerLength + packet.payloadBytes.size)
                .order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(headerLength)
        buffer.put(packet.headerBytes)
        buffer.put(packet.payloadBytes)

        return buffer.array()
    }

    /**
     * Zerlegt transportierte Bytes wieder in Header- und Payload-Bytes.
     *
     * @param bytes empfangenes ByteArray im Wire-Format
     * @return entpacktes [SerializedPacket]
     * @throws NetworkException wenn das Wire-Format ungültig ist
     */
    fun unpack(bytes: ByteArray): SerializedPacket {
        if (bytes.size < Int.SIZE_BYTES) {
            throw PacketTooShortException()
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val headerLength = buffer.int

        if (headerLength <= 0) {
            throw InvalidHeaderLengthException(headerLength)
        }
        if (bytes.size < Int.SIZE_BYTES + headerLength) {
            throw CorruptPacketException("Packet too short for declared header length.")
        }

        val headerBytes = ByteArray(headerLength)
        buffer[headerBytes]

        val payloadBytes = ByteArray(buffer.remaining())
        buffer[payloadBytes]

        return SerializedPacket(
            headerBytes = headerBytes,
            payloadBytes = payloadBytes,
        )
    }
}
