package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponseSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CreateLobbyErrorResponseTest {
    private val json = Json

    @Test
    fun `should serialize and deserialize create lobby error response`() {
        val response = CreateLobbyErrorResponse(reason = "Lobby konnte nicht erstellt werden.")

        val serialized = json.encodeToString(CreateLobbyErrorResponse.serializer(), response)
        val deserialized = json.decodeFromString<CreateLobbyErrorResponse>(serialized)

        assertEquals(
            """{"reason":"Lobby konnte nicht erstellt werden."}""",
            serialized,
        )
        assertEquals(response, deserialized)
    }

    @Test
    fun `should reject missing reason during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<CreateLobbyErrorResponse>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            CreateLobbyErrorResponseSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing field in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            CreateLobbyErrorResponseSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }
}
