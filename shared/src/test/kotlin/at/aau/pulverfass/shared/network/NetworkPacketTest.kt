package at.aau.pulverfass.shared.network

import at.aau.pulverfass.shared.networkmessage.LoginRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class NetworkPacketTest {
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
                header = MessageHeader(MessageType.LOGOUT_REQUEST),
                payload = LoginRequest(username = "alice", password = "secret"),
            )

        assertNotEquals(first, second)
    }
}
