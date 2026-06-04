package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.lobby.response.CharacterSelectResponse
import at.aau.pulverfass.shared.message.lobby.response.CharacterSelectResponseSerializer
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

class CharacterSelectResponseTest {
    private val json = Json

    @Test
    fun `should create character select response correctly`() {
        val response = CharacterSelectResponse(LobbyCode("AB12"), "warrior")

        assertEquals(LobbyCode("AB12"), response.lobbyCode)
        assertEquals("warrior", response.characterId)
    }

    @Test
    fun `should implement network message payload`() {
        val response = CharacterSelectResponse(LobbyCode("AB12"), "warrior")
        val payload: NetworkMessagePayload = response

        assertEquals(response, payload)
    }

    @Test
    fun `should serialize and deserialize character select response`() {
        val response = CharacterSelectResponse(LobbyCode("AB12"), "warrior")

        val serialized = json.encodeToString(CharacterSelectResponse.serializer(), response)
        val deserialized = json.decodeFromString<CharacterSelectResponse>(serialized)

        assertEquals(
            """{"lobbyCode":"AB12","characterId":"warrior"}""",
            serialized,
        )
        assertEquals(response, deserialized)
    }

    @Test
    fun `should resolve message type and serialization via registry`() {
        val response = CharacterSelectResponse(LobbyCode("CD34"), "character_03")

        val messageType = NetworkPayloadRegistry.messageTypeFor(response)
        val serialized = NetworkPayloadRegistry.serializePayload(response)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CHARACTER_SELECT_RESPONSE, messageType)
        assertEquals(response, deserialized)
    }

    @Test
    fun `should satisfy equals and hashCode contract`() {
        val a = CharacterSelectResponse(LobbyCode("EF56"), "character_04")
        val b = CharacterSelectResponse(LobbyCode("EF56"), "character_04")
        val c = CharacterSelectResponse(LobbyCode("EF56"), "character_03")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `should reject missing fields during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<CharacterSelectResponse>("""{"lobbyCode":"AB12"}""")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            CharacterSelectResponseSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing lobbyCode in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            CharacterSelectResponseSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }

    @Test
    fun `should reject missing characterId in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            CharacterSelectResponseSerializer.deserialize(
                SerializerTestDecoder(
                    intArrayOf(0, CompositeDecoder.DECODE_DONE),
                    scalarString = "AB12",
                ),
            )
        }
    }
}
