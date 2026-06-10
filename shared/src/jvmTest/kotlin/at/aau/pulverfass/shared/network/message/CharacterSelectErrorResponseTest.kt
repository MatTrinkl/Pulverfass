package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.lobby.response.error.CharacterSelectErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CharacterSelectErrorResponseSerializer
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CharacterSelectErrorResponseTest {
    private val json = Json

    @Test
    fun `should create character select error response correctly`() {
        val response = CharacterSelectErrorResponse("Charakter ist schon vergeben.")

        assertEquals("Charakter ist schon vergeben.", response.reason)
    }

    @Test
    fun `should implement network message payload`() {
        val response = CharacterSelectErrorResponse("Charakter ist schon vergeben.")
        val payload: NetworkMessagePayload = response

        assertEquals(response, payload)
    }

    @Test
    fun `should serialize and deserialize character select error response`() {
        val response = CharacterSelectErrorResponse("Achtung, dieser Charakter ist schon vergeben")

        val serialized = json.encodeToString(CharacterSelectErrorResponse.serializer(), response)
        val deserialized = json.decodeFromString<CharacterSelectErrorResponse>(serialized)

        assertEquals(
            """{"reason":"Achtung, dieser Charakter ist schon vergeben"}""",
            serialized,
        )
        assertEquals(response, deserialized)
    }

    @Test
    fun `should resolve message type and serialization via registry`() {
        val response = CharacterSelectErrorResponse("Conflict")

        val messageType = NetworkPayloadRegistry.messageTypeFor(response)
        val serialized = NetworkPayloadRegistry.serializePayload(response)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CHARACTER_SELECT_ERROR_RESPONSE, messageType)
        assertEquals(response, deserialized)
    }

    @Test
    fun `should satisfy equals and hashCode contract`() {
        val a = CharacterSelectErrorResponse("Conflict")
        val b = CharacterSelectErrorResponse("Conflict")
        val c = CharacterSelectErrorResponse("Other reason")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `should reject missing reason during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<CharacterSelectErrorResponse>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            CharacterSelectErrorResponseSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(99)),
            )
        }
    }

    @Test
    fun `should reject missing reason in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            CharacterSelectErrorResponseSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }
}
