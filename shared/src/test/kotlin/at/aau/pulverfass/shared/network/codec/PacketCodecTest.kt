package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.NetworkException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketCodecTest {
    @Test
    fun `should roundtrip packet with header and payload`() {
        val packet =
            SerializedPacket(
                headerBytes = byteArrayOf(1, 2, 3),
                payloadBytes = byteArrayOf(4, 5, 6),
            )

        val bytes = PacketCodec.pack(packet)
        val result = PacketCodec.unpack(bytes)

        assertEquals(packet, result)
    }

    @Test
    fun `should roundtrip packet with empty payload`() {
        val packet =
            SerializedPacket(
                headerBytes = byteArrayOf(9, 8),
                payloadBytes = byteArrayOf(),
            )

        val bytes = PacketCodec.pack(packet)
        val result = PacketCodec.unpack(bytes)

        assertArrayEquals(packet.headerBytes, result.headerBytes)
        assertArrayEquals(packet.payloadBytes, result.payloadBytes)
    }

    @Test
    fun `should reject packing packet with empty header`() {
        val packet = SerializedPacket(headerBytes = byteArrayOf(1), payloadBytes = byteArrayOf(2))

        SerializedPacket::class.java.getDeclaredField("_headerBytes").apply {
            isAccessible = true
            set(packet, byteArrayOf())
        }

        val exception =
            assertThrows(EmptyHeaderException::class.java) {
                PacketCodec.pack(packet)
            }

        assertEquals("Header must not be empty.", exception.message)
        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    fun `should reject unpack when byte array is shorter than header length field`() {
        val exception =
            assertThrows(PacketTooShortException::class.java) {
                PacketCodec.unpack(byteArrayOf(1, 2, 3))
            }

        assertEquals("Packet too short to contain required data.", exception.message)
        assertTrue(exception is NetworkException)
    }

    @Test
    fun `should reject unpack when declared header length is zero`() {
        val bytes =
            ByteBuffer.allocate(Int.SIZE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(0)
                .array()

        val exception =
            assertThrows(InvalidHeaderLengthException::class.java) {
                PacketCodec.unpack(bytes)
            }

        assertEquals("Invalid header length: 0", exception.message)
    }

    @Test
    fun `should reject unpack when declared header length exceeds remaining bytes`() {
        val bytes =
            ByteBuffer.allocate(Int.SIZE_BYTES + 2)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(5)
                .put(byteArrayOf(1, 2))
                .array()

        val exception =
            assertThrows(CorruptPacketException::class.java) {
                PacketCodec.unpack(bytes)
            }

        assertEquals("Packet too short for declared header length.", exception.message)
    }
}
