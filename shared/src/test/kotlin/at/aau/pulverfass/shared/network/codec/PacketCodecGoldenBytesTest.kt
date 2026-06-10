package at.aau.pulverfass.shared.network.codec

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

/**
 * Fixiert das binäre Wire-Format als Golden Bytes, damit Codec-Rewrites
 * (z. B. für Kotlin Multiplatform) byte-identisch bleiben müssen.
 */
class PacketCodecGoldenBytesTest {
    @Test
    fun packProducesDocumentedBigEndianWireFormat() {
        val packet =
            SerializedPacket(
                headerBytes = byteArrayOf(0x7B, 0x7D),
                payloadBytes = byteArrayOf(0x01, 0x02, 0x03),
            )

        val packed = PacketCodec.pack(packet)

        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x00, 0x02, 0x7B, 0x7D, 0x01, 0x02, 0x03),
            packed,
        )
    }

    @Test
    fun packEncodesHeaderLengthAbove255AsBigEndianInt() {
        val packet =
            SerializedPacket(
                headerBytes = ByteArray(300) { 0x41 },
                payloadBytes = byteArrayOf(),
            )

        val packed = PacketCodec.pack(packet)

        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x01, 0x2C),
            packed.copyOfRange(0, 4),
        )
    }

    @Test
    fun unpackRestoresGoldenBytesToHeaderAndPayload() {
        val unpacked =
            PacketCodec.unpack(
                byteArrayOf(0x00, 0x00, 0x00, 0x02, 0x7B, 0x7D, 0x01, 0x02, 0x03),
            )

        assertArrayEquals(byteArrayOf(0x7B, 0x7D), unpacked.headerBytes)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), unpacked.payloadBytes)
    }
}
