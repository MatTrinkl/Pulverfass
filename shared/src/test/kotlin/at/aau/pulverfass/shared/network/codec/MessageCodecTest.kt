package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.exception.InvalidSerializedPacketException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.message.GameJoinRequest
import at.aau.pulverfass.shared.network.message.LoginRequest
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageCodecTest {
    @Test
    fun `should encode and decode login request payload directly`() {
        val payload = LoginRequest(username = "alice", password = "secret")

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode game join payload directly`() {
        val payload = GameJoinRequest(at.aau.pulverfass.shared.ids.LobbyCode("AB12"))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

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
            assertThrows(InvalidSerializedPacketException::class.java) {
                MessageCodec.decodePayload(byteArrayOf(1, 2, 3))
            }

        assertTrue(exception.message!!.contains("Packet too short"))
    }

    @Test
    fun `should reject unsupported payload classes`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                MessageCodec.encode(UnsupportedPayload)
            }

        assertEquals(
            "Unsupported payload class: ${UnsupportedPayload::class.java.name}",
            exception.message,
        )
    }

    private data object UnsupportedPayload :
        at.aau.pulverfass.shared.network.message.NetworkMessagePayload
}
