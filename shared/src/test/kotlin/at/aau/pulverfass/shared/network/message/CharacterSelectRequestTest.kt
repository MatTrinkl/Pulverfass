package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.lobby.request.CharacterSelectRequest
import at.aau.pulverfass.shared.message.lobby.request.CharacterSelectRequestSerializer
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

class CharacterSelectRequestTest {
    private val json = Json

    @Test
    fun `should create character select request correctly`() {
        val request = CharacterSelectRequest(LobbyCode("AB12"), PlayerId(5), "warrior")

        assertEquals(LobbyCode("AB12"), request.lobbyCode)
        assertEquals(PlayerId(5), request.playerId)
        assertEquals("warrior", request.characterId)
    }

    @Test
    fun `should implement network message payload`() {
        val request = CharacterSelectRequest(LobbyCode("AB12"), PlayerId(5), "warrior")
        val payload: NetworkMessagePayload = request

        assertEquals(request, payload)
    }

    @Test
    fun `should serialize and deserialize character select request`() {
        val request = CharacterSelectRequest(LobbyCode("AB12"), PlayerId(5), "warrior")

        val serialized = json.encodeToString(CharacterSelectRequest.serializer(), request)
        val deserialized = json.decodeFromString<CharacterSelectRequest>(serialized)

        assertEquals(
            """{"lobbyCode":"AB12","playerId":5,"characterId":"warrior"}""",
            serialized,
        )
        assertEquals(request, deserialized)
    }

    @Test
    fun `should resolve message type and serialization via registry`() {
        val request = CharacterSelectRequest(LobbyCode("CD34"), PlayerId(7), "ice")

        val messageType = NetworkPayloadRegistry.messageTypeFor(request)
        val serialized = NetworkPayloadRegistry.serializePayload(request)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CHARACTER_SELECT_REQUEST, messageType)
        assertEquals(request, deserialized)
    }

    @Test
    fun `should satisfy equals and hashCode contract`() {
        val a = CharacterSelectRequest(LobbyCode("EF56"), PlayerId(9), "doctor")
        val b = CharacterSelectRequest(LobbyCode("EF56"), PlayerId(9), "doctor")
        val c = CharacterSelectRequest(LobbyCode("EF56"), PlayerId(9), "ice")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `should reject missing fields during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<CharacterSelectRequest>("""{"lobbyCode":"AB12"}""")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            CharacterSelectRequestSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing lobbyCode in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            CharacterSelectRequestSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }

    @Test
    fun `should reject missing playerId in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            CharacterSelectRequestSerializer.deserialize(
                SerializerTestDecoder(
                    intArrayOf(0, CompositeDecoder.DECODE_DONE),
                    scalarString = "AB12",
                ),
            )
        }
    }

    @Test
    fun `should reject missing characterId in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            CharacterSelectRequestSerializer.deserialize(
                SerializerTestDecoder(
                    intArrayOf(0, 1, CompositeDecoder.DECODE_DONE),
                    scalarString = "AB12",
                    scalarLong = 5L,
                ),
            )
        }
    }
}
