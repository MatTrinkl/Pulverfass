package at.aau.pulverfass.shared.network.receive

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.PacketReceiveException
import at.aau.pulverfass.shared.network.codec.PacketCodec
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.message.NetworkMessageSerializer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PacketReceiveAdapterTest {
    private val adapter = PacketReceiveAdapter()

    @Test
    fun `should unpack valid packet and read header`() {
        val connectionId = ConnectionId(1)
        val header = MessageHeader(MessageType.LOGIN_REQUEST)
        val packet =
            SerializedPacket(
                headerBytes = NetworkMessageSerializer.serializeHeader(header),
                payloadBytes = byteArrayOf(1, 2, 3),
            )
        val bytes = PacketCodec.pack(packet)

        val result = adapter.decode(connectionId, bytes)

        assertEquals(connectionId, result.connectionId)
        assertEquals(header, result.header)
        assertEquals(packet, result.packet)
        assertArrayEquals(byteArrayOf(1, 2, 3), result.packet.payloadBytes)
    }

    @Test
    fun `should translate corrupt packet into receive exception`() {
        val connectionId = ConnectionId(2)

        val exception =
            assertThrows(PacketReceiveException::class.java) {
                adapter.decode(connectionId, byteArrayOf(1, 2, 3))
            }

        assertEquals(connectionId, exception.connectionId)
        assertTrue(exception.message!!.contains("Failed to decode received packet"))
        assertTrue(exception.cause!!.message!!.contains("Packet too short"))
    }

    @Test
    fun `should expose message type from decoded header`() {
        val connectionId = ConnectionId(3)
        val header = MessageHeader(MessageType.HEARTBEAT)
        val bytes =
            PacketCodec.pack(
                SerializedPacket(
                    headerBytes = NetworkMessageSerializer.serializeHeader(header),
                    payloadBytes = byteArrayOf(),
                ),
            )

        val result = adapter.decode(connectionId, bytes)

        assertEquals(MessageType.HEARTBEAT, result.header.type)
    }

    @Test
    fun `should translate invalid header bytes into receive exception`() {
        val connectionId = ConnectionId(4)
        val bytes =
            PacketCodec.pack(
                SerializedPacket(
                    headerBytes = "not-json".encodeToByteArray(),
                    payloadBytes = byteArrayOf(),
                ),
            )

        val exception =
            assertThrows(PacketReceiveException::class.java) {
                adapter.decode(connectionId, bytes)
            }

        assertEquals(connectionId, exception.connectionId)
        assertTrue(exception.cause!!.message!!.contains("deserialize message header"))
    }
}
