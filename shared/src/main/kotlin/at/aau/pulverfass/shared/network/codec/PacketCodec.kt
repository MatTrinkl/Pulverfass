package at.aau.pulverfass.shared.network.codec

/**
 * Kodiert und dekodiert das binäre Transportformat eines [SerializedPacket]s.
 *
 * Dieser Codec ist ausschließlich für das Byte-Framing verantwortlich. Er kennt
 * keine fachlichen Nachrichtentypen und arbeitet nur mit bereits serialisierten
 * Header- und Payload-Bytes.
 *
 * Das Wire-Format lautet:
 * `[Int32 headerLength][headerBytes][payloadBytes]` (Big-Endian)
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
        val totalPacketSize =
            Int.SIZE_BYTES.toLong() + headerLength.toLong() + packet.payloadBytes.size.toLong()
        require(totalPacketSize <= Int.MAX_VALUE.toLong()) {
            "Packet exceeds the maximum supported size of ${Int.MAX_VALUE} bytes."
        }

        val result = ByteArray(totalPacketSize.toInt())
        result.writeIntBigEndian(offset = 0, value = headerLength)
        packet.headerBytes.copyInto(result, destinationOffset = Int.SIZE_BYTES)
        packet.payloadBytes.copyInto(result, destinationOffset = Int.SIZE_BYTES + headerLength)
        return result
    }

    /**
     * Zerlegt transportierte Bytes wieder in Header- und Payload-Bytes.
     *
     * @param bytes empfangenes ByteArray im Wire-Format
     * @return entpacktes [SerializedPacket]
     * @throws at.aau.pulverfass.shared.network.exception.NetworkException wenn
     * das Wire-Format ungueltig ist
     */
    fun unpack(bytes: ByteArray): SerializedPacket {
        if (bytes.size < Int.SIZE_BYTES) {
            throw PacketTooShortException()
        }

        val headerLength = bytes.readIntBigEndian(offset = 0)
        if (headerLength <= 0) {
            throw InvalidHeaderLengthException(headerLength)
        }
        val remainingBytes = bytes.size - Int.SIZE_BYTES
        if (remainingBytes < headerLength) {
            throw CorruptPacketException("Packet too short for declared header length.")
        }

        val headerEnd = Int.SIZE_BYTES + headerLength
        return SerializedPacket(
            headerBytes = bytes.copyOfRange(Int.SIZE_BYTES, headerEnd),
            payloadBytes = bytes.copyOfRange(headerEnd, bytes.size),
        )
    }

    private fun ByteArray.writeIntBigEndian(
        offset: Int,
        value: Int,
    ) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    private fun ByteArray.readIntBigEndian(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
}
