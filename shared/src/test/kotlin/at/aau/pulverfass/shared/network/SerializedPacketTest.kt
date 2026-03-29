package at.aau.pulverfass.shared.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SerializedPacketTest {
    @Test
    fun `should compare equal when byte arrays have same content`() {
        val first =
            SerializedPacket(headerBytes = byteArrayOf(1, 2), payloadBytes = byteArrayOf(3, 4))
        val second =
            SerializedPacket(headerBytes = byteArrayOf(1, 2), payloadBytes = byteArrayOf(3, 4))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `should expose header and payload bytes`() {
        val packet =
            SerializedPacket(headerBytes = byteArrayOf(1, 2), payloadBytes = byteArrayOf(3, 4))

        assertEquals(listOf<Byte>(1, 2), packet.headerBytes.toList())
        assertEquals(listOf<Byte>(3, 4), packet.payloadBytes.toList())
    }

    @Test
    fun `should return true when comparing same instance`() {
        val packet = SerializedPacket(headerBytes = byteArrayOf(7), payloadBytes = byteArrayOf(8))

        assertEquals(packet, packet)
    }

    @Test
    fun `should return false when comparing with null`() {
        val packet = SerializedPacket(headerBytes = byteArrayOf(1), payloadBytes = byteArrayOf(2))

        assertNotEquals(packet, null)
    }

    @Test
    fun `should return false when comparing with different type`() {
        val packet = SerializedPacket(headerBytes = byteArrayOf(1), payloadBytes = byteArrayOf(2))

        assertNotEquals(packet, "not a packet")
    }

    @Test
    fun `should return false when header bytes differ`() {
        val first =
            SerializedPacket(headerBytes = byteArrayOf(1, 2), payloadBytes = byteArrayOf(3, 4))
        val second =
            SerializedPacket(headerBytes = byteArrayOf(9, 2), payloadBytes = byteArrayOf(3, 4))

        assertNotEquals(first, second)
    }

    @Test
    fun `should return false when payload bytes differ`() {
        val first =
            SerializedPacket(headerBytes = byteArrayOf(1, 2), payloadBytes = byteArrayOf(3, 4))
        val second =
            SerializedPacket(headerBytes = byteArrayOf(1, 2), payloadBytes = byteArrayOf(3, 9))

        assertNotEquals(first, second)
    }

    @Test
    fun `should not be affected by mutation of constructor arguments after construction`() {
        val header = byteArrayOf(1, 2)
        val payload = byteArrayOf(3, 4)
        val packet = SerializedPacket(headerBytes = header, payloadBytes = payload)

        header[0] = 99.toByte()
        payload[0] = 99.toByte()

        assertEquals(listOf<Byte>(1, 2), packet.headerBytes.toList())
        assertEquals(listOf<Byte>(3, 4), packet.payloadBytes.toList())
    }

    @Test
    fun `should not be affected by mutation of returned accessor arrays`() {
        val packet =
            SerializedPacket(headerBytes = byteArrayOf(1, 2), payloadBytes = byteArrayOf(3, 4))

        packet.headerBytes[0] = 99.toByte()
        packet.payloadBytes[0] = 99.toByte()

        assertEquals(listOf<Byte>(1, 2), packet.headerBytes.toList())
        assertEquals(listOf<Byte>(3, 4), packet.payloadBytes.toList())
    }

    @Test
    fun `should reject empty header bytes`() {
        val exception =
            assertThrows(InvalidSerializedPacketException::class.java) {
                SerializedPacket(headerBytes = byteArrayOf(), payloadBytes = byteArrayOf(1))
            }

        assertEquals("SerializedPacket headerBytes must not be empty", exception.message)
    }
}
