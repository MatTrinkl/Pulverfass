package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequestSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReconnectRequestTest {
    private val json = Json

    @Test
    fun `should create reconnect request correctly`() {
        val request = ReconnectRequest(SessionToken("123e4567-e89b-12d3-a456-426614174110"))

        assertEquals(
            SessionToken("123e4567-e89b-12d3-a456-426614174110"),
            request.sessionToken,
        )
    }

    @Test
    fun `should implement network message payload`() {
        val request = ReconnectRequest(SessionToken("123e4567-e89b-12d3-a456-426614174111"))
        val payload: NetworkMessagePayload = request

        assertEquals(request, payload)
    }

    @Test
    fun `should serialize and deserialize reconnect request`() {
        val request = ReconnectRequest(SessionToken("123e4567-e89b-12d3-a456-426614174112"))

        val serialized = json.encodeToString(ReconnectRequest.serializer(), request)
        val deserialized = json.decodeFromString<ReconnectRequest>(serialized)

        assertEquals("""{"sessionToken":"123e4567-e89b-12d3-a456-426614174112"}""", serialized)
        assertEquals(request, deserialized)
    }

    @Test
    fun `should reject missing session token during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<ReconnectRequest>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReconnectRequestSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `should reject missing field in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            ReconnectRequestSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }

    @Test
    fun `should decode direct serializer path with configured scalar string`() {
        val decoded =
            ReconnectRequestSerializer.deserialize(
                SerializerTestDecoder(
                    indices = intArrayOf(0, CompositeDecoder.DECODE_DONE),
                    scalarString = "123e4567-e89b-12d3-a456-426614174113",
                ),
            )

        assertEquals(
            SessionToken("123e4567-e89b-12d3-a456-426614174113"),
            decoded.sessionToken,
        )
    }

    @Test
    fun `descriptor should use stable shared network message serial name`() {
        assertEquals(
            "at.aau.pulverfass.shared.network.message.ReconnectRequest",
            ReconnectRequestSerializer.descriptor.serialName,
        )
    }
}
