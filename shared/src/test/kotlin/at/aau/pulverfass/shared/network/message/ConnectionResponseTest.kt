package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponseSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConnectionResponseTest {
    private val json = Json

    @Test
    fun `should create connection response correctly`() {
        val response = ConnectionResponse(SessionToken("123e4567-e89b-12d3-a456-426614174000"))

        assertEquals(SessionToken("123e4567-e89b-12d3-a456-426614174000"), response.sessionToken)
    }

    @Test
    fun `should implement network message payload`() {
        val response = ConnectionResponse(SessionToken("123e4567-e89b-12d3-a456-426614174001"))
        val payload: NetworkMessagePayload = response

        assertEquals(response, payload)
    }

    @Test
    fun `should serialize and deserialize connection response`() {
        val response = ConnectionResponse(SessionToken("123e4567-e89b-12d3-a456-426614174002"))

        val serialized = json.encodeToString(ConnectionResponse.serializer(), response)
        val deserialized = json.decodeFromString<ConnectionResponse>(serialized)

        assertEquals("""{"sessionToken":"123e4567-e89b-12d3-a456-426614174002"}""", serialized)
        assertEquals(response, deserialized)
    }

    @Test
    fun `should reject missing session token during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<ConnectionResponse>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionResponseSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `should reject missing field in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            ConnectionResponseSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }

    @Test
    fun `should decode direct serializer path with configured scalar string`() {
        val decoded =
            ConnectionResponseSerializer.deserialize(
                SerializerTestDecoder(
                    indices = intArrayOf(0, CompositeDecoder.DECODE_DONE),
                    scalarString = "123e4567-e89b-12d3-a456-426614174003",
                ),
            )

        assertEquals(
            SessionToken("123e4567-e89b-12d3-a456-426614174003"),
            decoded.sessionToken,
        )
    }
}
