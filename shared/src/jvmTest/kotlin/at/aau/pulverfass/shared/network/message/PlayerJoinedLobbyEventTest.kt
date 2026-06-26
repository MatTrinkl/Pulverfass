package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEventSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerJoinedLobbyEventTest {
    private val json = Json

    @Test
    fun `should create player joined lobby event correctly`() {
        val event = PlayerJoinedLobbyEvent(LobbyCode("1003"), PlayerId(7), "Alice")

        assertEquals(LobbyCode("1003"), event.lobbyCode)
        assertEquals(PlayerId(7), event.playerId)
        assertEquals("Alice", event.playerDisplayName)
    }

    @Test
    fun `should implement network message payload`() {
        val event = PlayerJoinedLobbyEvent(LobbyCode("1071"), PlayerId(8), "Bob")
        val payload: NetworkMessagePayload = event

        assertEquals(event, payload)
    }

    @Test
    fun `should serialize and deserialize player joined lobby event`() {
        val event = PlayerJoinedLobbyEvent(LobbyCode("1132"), PlayerId(9), "Carol")

        val serialized = json.encodeToString(PlayerJoinedLobbyEvent.serializer(), event)
        val deserialized = json.decodeFromString<PlayerJoinedLobbyEvent>(serialized)

        assertEquals(
            """{"lobbyCode":"1132","playerId":9,"playerDisplayName":"Carol"}""",
            serialized,
        )
        assertEquals(event, deserialized)
    }

    @Test
    fun `should serialize isHost only when true`() {
        val event = PlayerJoinedLobbyEvent(LobbyCode("1132"), PlayerId(9), "Carol", isHost = true)

        val serialized = json.encodeToString(PlayerJoinedLobbyEvent.serializer(), event)
        val deserialized = json.decodeFromString<PlayerJoinedLobbyEvent>(serialized)

        assertEquals(
            """{"lobbyCode":"1132","playerId":9,"playerDisplayName":"Carol","isHost":true}""",
            serialized,
        )
        assertEquals(event, deserialized)
    }

    @Test
    fun `should reject blank player display name`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                PlayerJoinedLobbyEvent(LobbyCode("1003"), PlayerId(7), " ")
            }

        assertTrue(exception.message.orEmpty().contains("playerDisplayName"))
    }

    @Test
    fun `should reject player display name with invalid characters`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                PlayerJoinedLobbyEvent(LobbyCode("1003"), PlayerId(7), "ALICE1")
            }

        assertTrue(exception.message.orEmpty().contains("playerDisplayName"))
    }

    @Test
    fun `should reject missing fields during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<PlayerJoinedLobbyEvent>("""{"lobbyCode":"1003"}""")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlayerJoinedLobbyEventSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing fields in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            PlayerJoinedLobbyEventSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
        assertThrows(MissingFieldException::class.java) {
            PlayerJoinedLobbyEventSerializer.deserialize(
                SerializerTestDecoder(
                    intArrayOf(0, CompositeDecoder.DECODE_DONE),
                    scalarString = "1003",
                ),
            )
        }
    }
}
