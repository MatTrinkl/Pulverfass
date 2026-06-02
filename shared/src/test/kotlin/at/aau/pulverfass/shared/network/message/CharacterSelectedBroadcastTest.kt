package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.lobby.event.CharacterSelectedBroadcast
import at.aau.pulverfass.shared.message.lobby.event.CharacterSelectedBroadcastSerializer
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

class CharacterSelectedBroadcastTest {
    private val json = Json

    @Test
    fun `should create character selected broadcast correctly`() {
        val broadcast = CharacterSelectedBroadcast(LobbyCode("AB12"), PlayerId(3), "warrior")

        assertEquals(LobbyCode("AB12"), broadcast.lobbyCode)
        assertEquals(PlayerId(3), broadcast.playerId)
        assertEquals("warrior", broadcast.characterId)
    }

    @Test
    fun `should implement network message payload`() {
        val broadcast = CharacterSelectedBroadcast(LobbyCode("AB12"), PlayerId(3), "warrior")
        val payload: NetworkMessagePayload = broadcast

        assertEquals(broadcast, payload)
    }

    @Test
    fun `should serialize and deserialize character selected broadcast`() {
        val broadcast = CharacterSelectedBroadcast(LobbyCode("AB12"), PlayerId(3), "warrior")

        val serialized = json.encodeToString(CharacterSelectedBroadcast.serializer(), broadcast)
        val deserialized = json.decodeFromString<CharacterSelectedBroadcast>(serialized)

        assertEquals(
            """{"lobbyCode":"AB12","playerId":3,"characterId":"warrior"}""",
            serialized,
        )
        assertEquals(broadcast, deserialized)
    }

    @Test
    fun `should resolve message type and serialization via registry`() {
        val broadcast = CharacterSelectedBroadcast(LobbyCode("CD34"), PlayerId(7), "ice")

        val messageType = NetworkPayloadRegistry.messageTypeFor(broadcast)
        val serialized = NetworkPayloadRegistry.serializePayload(broadcast)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CHARACTER_SELECTED_BROADCAST, messageType)
        assertEquals(broadcast, deserialized)
    }

    @Test
    fun `should satisfy equals and hashCode contract`() {
        val a = CharacterSelectedBroadcast(LobbyCode("EF56"), PlayerId(9), "doctor")
        val b = CharacterSelectedBroadcast(LobbyCode("EF56"), PlayerId(9), "doctor")
        val c = CharacterSelectedBroadcast(LobbyCode("EF56"), PlayerId(9), "ice")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `should reject missing fields during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<CharacterSelectedBroadcast>("""{"lobbyCode":"AB12"}""")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            CharacterSelectedBroadcastSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(99)),
            )
        }
    }

    @Test
    fun `should reject missing lobbyCode in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            CharacterSelectedBroadcastSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }

    @Test
    fun `should reject missing playerId in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            CharacterSelectedBroadcastSerializer.deserialize(
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
            CharacterSelectedBroadcastSerializer.deserialize(
                SerializerTestDecoder(
                    intArrayOf(0, 1, CompositeDecoder.DECODE_DONE),
                    scalarString = "AB12",
                    scalarLong = 3L,
                ),
            )
        }
    }
}
