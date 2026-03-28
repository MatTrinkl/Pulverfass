package at.aau.pulverfass.shared.networkmessage

import at.aau.pulverfass.shared.network.NetworkMessagePayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoginRequestTest {
    private val json = Json

    @Test
    fun `should create login request correctly`() {
        val request = LoginRequest(username = "alice", password = "secret")

        assertEquals("alice", request.username)
        assertEquals("secret", request.password)
    }

    @Test
    fun `should implement network message payload`() {
        val request = LoginRequest(username = "bob", password = "hunter2")
        val payload: NetworkMessagePayload = request

        assertEquals(request, payload)
    }

    @Test
    fun `should serialize and deserialize login request`() {
        val request = LoginRequest(username = "charlie", password = "topsecret")

        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<LoginRequest>(serialized)

        assertEquals("""{"username":"charlie","password":"topsecret"}""", serialized)
        assertEquals(request, deserialized)
    }
}
