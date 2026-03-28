package at.aau.pulverfass.shared.network

import at.aau.pulverfass.shared.networkmessage.LoginRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkMessageSerializerTest {
    @Test
    fun `should serialize and deserialize header`() {
        val header = MessageHeader(MessageType.LOGIN_REQUEST)

        val bytes = NetworkMessageSerializer.serializeHeader(header)
        val result = NetworkMessageSerializer.deserializeHeader(bytes)

        assertEquals(header, result)
    }

    @Test
    fun `should deserialize login request payload for login request type`() {
        val payload = LoginRequest(username = "alice", password = "secret")
        val bytes = NetworkMessageSerializer.serializePayload(LoginRequest.serializer(), payload)

        val result = NetworkMessageSerializer.deserializePayload(MessageType.LOGIN_REQUEST, bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should throw for unsupported payload type`() {
        val payloadBytes = """{"username":"alice","password":"secret"}""".encodeToByteArray()

        val exception =
            assertThrows(UnsupportedPayloadTypeException::class.java) {
                NetworkMessageSerializer.deserializePayload(
                    MessageType.LOGIN_RESPONSE,
                    payloadBytes,
                )
            }

        assertEquals("Unsupported payload type: LOGIN_RESPONSE", exception.message)
    }

    @Test
    fun `should wrap invalid header deserialization in network serialization exception`() {
        val exception =
            assertThrows(NetworkSerializationException::class.java) {
                NetworkMessageSerializer.deserializeHeader(
                    """{"type":"NOT_REAL"}""".encodeToByteArray(),
                )
            }

        assertEquals("Failed to deserialize message header", exception.message)
        assertTrue(exception.cause != null)
    }

    @Test
    fun `should wrap invalid payload deserialization in network serialization exception`() {
        val exception =
            assertThrows(NetworkSerializationException::class.java) {
                NetworkMessageSerializer.deserializePayload(
                    MessageType.LOGIN_REQUEST,
                    """{"username":123,"password":true}""".encodeToByteArray(),
                )
            }

        assertEquals(
            "Failed to deserialize payload for message type LOGIN_REQUEST",
            exception.message,
        )
        assertTrue(exception.cause != null)
    }
}
