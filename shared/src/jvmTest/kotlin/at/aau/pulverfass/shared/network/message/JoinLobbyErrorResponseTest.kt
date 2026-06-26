package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponseSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JoinLobbyErrorResponseTest {
    private val json = Json

    @Test
    fun `should serialize and deserialize join lobby error response`() {
        val response = JoinLobbyErrorResponse(reason = "Lobby wurde nicht gefunden.")

        val serialized = json.encodeToString(JoinLobbyErrorResponse.serializer(), response)
        val deserialized = json.decodeFromString<JoinLobbyErrorResponse>(serialized)

        assertEquals("""{"reason":"Lobby wurde nicht gefunden."}""", serialized)
        assertEquals(response, deserialized)
    }

    @Test
    fun `should reject missing reason during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<JoinLobbyErrorResponse>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            JoinLobbyErrorResponseSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing field in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            JoinLobbyErrorResponseSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }
}
