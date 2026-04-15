package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEventSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PlayerLeftLobbyEventTest {
    private val json = Json

    @Test
    fun `should create player left lobby event correctly`() {
        val event = PlayerLeftLobbyEvent(LobbyCode("AB12"), PlayerId(7))

        assertEquals(LobbyCode("AB12"), event.lobbyCode)
        assertEquals(PlayerId(7), event.playerId)
    }

    @Test
    fun `should implement network message payload`() {
        val event = PlayerLeftLobbyEvent(LobbyCode("CD34"), PlayerId(8))
        val payload: NetworkMessagePayload = event

        assertEquals(event, payload)
    }

    @Test
    fun `should serialize and deserialize player left lobby event`() {
        val event = PlayerLeftLobbyEvent(LobbyCode("EF56"), PlayerId(9))

        val serialized = json.encodeToString(PlayerLeftLobbyEvent.serializer(), event)
        val deserialized = json.decodeFromString<PlayerLeftLobbyEvent>(serialized)

        assertEquals("""{"lobbyCode":"EF56","playerId":9}""", serialized)
        assertEquals(event, deserialized)
    }

    @Test
    fun `should reject missing fields during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<PlayerLeftLobbyEvent>("""{"lobbyCode":"AB12"}""")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlayerLeftLobbyEventSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing fields in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            PlayerLeftLobbyEventSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
        assertThrows(MissingFieldException::class.java) {
            PlayerLeftLobbyEventSerializer.deserialize(
                SerializerTestDecoder(
                    intArrayOf(0, CompositeDecoder.DECODE_DONE),
                    scalarString = "AB12",
                ),
            )
        }
    }
}
