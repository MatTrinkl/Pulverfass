package at.aau.pulverfass.shared.network

import at.aau.pulverfass.shared.networkmessage.LoginRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NetworkMessageSerializerTest {
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
            assertThrows(IllegalArgumentException::class.java) {
                NetworkMessageSerializer.deserializePayload(
                    MessageType.LOGIN_RESPONSE,
                    payloadBytes,
                )
            }

        assertEquals("Unsupported payload type: LOGIN_RESPONSE", exception.message)
    }
}
