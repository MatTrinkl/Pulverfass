package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.PayloadTypeMismatchException
import at.aau.pulverfass.shared.network.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.message.LoginRequest
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.message.NetworkMessagePayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NetworkPacketTest {
    private data class UnknownPayload(
        val value: String,
    ) : NetworkMessagePayload

    @Test
    fun `should create network packet correctly`() {
        val payload = LoginRequest(username = "alice", password = "secret")

        val packet =
            NetworkPacket(header = MessageHeader(MessageType.LOGIN_REQUEST), payload = payload)

        assertEquals(MessageType.LOGIN_REQUEST, packet.header.type)
        assertEquals(payload, packet.payload)
    }

    @Test
    fun `should support equality for same header and payload`() {
        val first =
            NetworkPacket(
                header = MessageHeader(MessageType.LOGIN_REQUEST),
                payload = LoginRequest(username = "alice", password = "secret"),
            )
        val second =
            NetworkPacket(
                header = MessageHeader(MessageType.LOGIN_REQUEST),
                payload = LoginRequest(username = "alice", password = "secret"),
            )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `should detect different packets`() {
        val first =
            NetworkPacket(
                header = MessageHeader(MessageType.LOGIN_REQUEST),
                payload = LoginRequest(username = "alice", password = "secret"),
            )
        val second =
            NetworkPacket(
                header = MessageHeader(MessageType.LOGIN_REQUEST),
                payload = LoginRequest(username = "bob", password = "secret"),
            )

        assertNotEquals(first, second)
    }

    @Test
    fun `should reject packet when header type does not match payload type`() {
        val exception =
            assertThrows(PayloadTypeMismatchException::class.java) {
                NetworkPacket(
                    header = MessageHeader(MessageType.LOGOUT_REQUEST),
                    payload = LoginRequest(username = "alice", password = "secret"),
                )
            }

        assertEquals(
            "Header type LOGOUT_REQUEST does not match payload type LOGIN_REQUEST",
            exception.message,
        )
    }

    @Test
    fun `should reject packet with unregistered payload class`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                NetworkPacket(
                    header = MessageHeader(MessageType.LOGIN_REQUEST),
                    payload = UnknownPayload("test"),
                )
            }

        assertEquals(
            "Unsupported payload class: ${UnknownPayload::class.java.name}",
            exception.message,
        )
    }
}
