package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.InvalidSerializedPacketException
import at.aau.pulverfass.shared.network.message.LoginRequest
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageCodecTest {
    @Test
    fun `should encode and decode login request packet`() {
        val packet =
            NetworkPacket(
                header = MessageHeader(MessageType.LOGIN_REQUEST),
                payload = LoginRequest(username = "alice", password = "secret"),
            )

        val bytes = MessageCodec.encode(packet, LoginRequest.serializer())
        val result = MessageCodec.decode(bytes)

        assertEquals(packet.header, result.header)
        assertEquals(packet.payload, result.payload)
    }

    @Test
    fun `should wrap malformed frame as invalid serialized packet exception`() {
        val exception =
            Assertions.assertThrows(InvalidSerializedPacketException::class.java) {
                MessageCodec.decode(byteArrayOf(1, 2, 3))
            }

        assertTrue(exception.message!!.contains("Packet too short"))
    }
}
