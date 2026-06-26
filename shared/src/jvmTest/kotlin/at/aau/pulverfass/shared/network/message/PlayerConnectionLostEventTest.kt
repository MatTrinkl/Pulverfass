package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEventSerializer
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostReason
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PlayerConnectionLostEventTest {
    private val json = Json

    @Test
    fun `should create player connection lost event correctly`() {
        val event =
            PlayerConnectionLostEvent(
                LobbyCode("1003"),
                PlayerId(7),
                PlayerConnectionLostReason.SOCKET_CLOSED,
            )

        assertEquals(LobbyCode("1003"), event.lobbyCode)
        assertEquals(PlayerId(7), event.playerId)
        assertEquals(PlayerConnectionLostReason.SOCKET_CLOSED, event.reason)
    }

    @Test
    fun `should implement network message payload`() {
        val event =
            PlayerConnectionLostEvent(
                LobbyCode("1071"),
                PlayerId(8),
                PlayerConnectionLostReason.HEARTBEAT_TIMEOUT,
            )
        val payload: NetworkMessagePayload = event

        assertEquals(event, payload)
    }

    @Test
    fun `should serialize and deserialize player connection lost event`() {
        val event =
            PlayerConnectionLostEvent(
                LobbyCode("1132"),
                PlayerId(9),
                PlayerConnectionLostReason.HEARTBEAT_TIMEOUT,
            )

        val serialized = json.encodeToString(PlayerConnectionLostEvent.serializer(), event)
        val deserialized = json.decodeFromString<PlayerConnectionLostEvent>(serialized)

        assertEquals(
            """{"lobbyCode":"1132","playerId":9,"reason":"HEARTBEAT_TIMEOUT"}""",
            serialized,
        )
        assertEquals(event, deserialized)
    }

    @Test
    fun `should reject missing fields during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<PlayerConnectionLostEvent>(
                """{"lobbyCode":"1003","playerId":9}""",
            )
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlayerConnectionLostEventSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing fields in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            PlayerConnectionLostEventSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
        assertThrows(MissingFieldException::class.java) {
            PlayerConnectionLostEventSerializer.deserialize(
                SerializerTestDecoder(
                    intArrayOf(0, 1, CompositeDecoder.DECODE_DONE),
                    scalarString = "1003",
                    scalarLong = 9L,
                ),
            )
        }
    }
}
