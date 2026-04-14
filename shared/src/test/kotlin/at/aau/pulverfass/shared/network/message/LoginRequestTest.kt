package at.aau.pulverfass.shared.network.message

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

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

    @Test
    fun `should reject null username at constructor boundary`() {
        val constructor =
            LoginRequest::class.java.declaredConstructors.first { it.parameterCount == 2 }
        val valid = constructor.newInstance("dave", "secret") as LoginRequest

        assertEquals("dave", valid.username)

        assertThrows(InvocationTargetException::class.java) {
            constructor.newInstance(null, "secret")
        }
    }

    @Test
    fun `should reject missing fields during deserialization`() {
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            json.decodeFromString<LoginRequest>("""{"username":"alice"}""")
        }
    }

    @Test
    fun `should reject missing username during deserialization`() {
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            json.decodeFromString<LoginRequest>("""{"password":"secret"}""")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            LoginRequestSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing fields in serializer directly`() {
        assertThrows(kotlinx.serialization.MissingFieldException::class.java) {
            LoginRequestSerializer.deserialize(
                SerializerTestDecoder(
                    intArrayOf(kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE),
                ),
            )
        }
        assertThrows(kotlinx.serialization.MissingFieldException::class.java) {
            LoginRequestSerializer.deserialize(
                SerializerTestDecoder(
                    intArrayOf(0, kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE),
                    mapOf(0 to "alice"),
                ),
            )
        }
    }
}
