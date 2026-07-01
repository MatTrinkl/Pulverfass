package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import at.aau.pulverfass.shared.message.lobby.event.ConnectionStatusUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.ConnectionStatusUpdateEventSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConnectionStatusUpdateEventTest {
    private val json = Json

    @Test
    fun `should create connection status update event correctly`() {
        val event =
            ConnectionStatusUpdateEvent(
                lobbyCode = LobbyCode("1003"),
                playerId = PlayerId(7),
                status = ConnectionStatus.CONNECTED,
            )
        val payload: NetworkMessagePayload = event

        assertEquals(LobbyCode("1003"), event.lobbyCode)
        assertEquals(PlayerId(7), event.playerId)
        assertEquals(ConnectionStatus.CONNECTED, event.status)
        assertEquals(event, payload)
    }

    @Test
    fun `should serialize and deserialize all connection status updates`() {
        ConnectionStatus.entries.forEach { status ->
            val event =
                ConnectionStatusUpdateEvent(
                    lobbyCode = LobbyCode("1071"),
                    playerId = PlayerId(8),
                    status = status,
                )

            val serialized = json.encodeToString(ConnectionStatusUpdateEvent.serializer(), event)
            val deserialized = json.decodeFromString<ConnectionStatusUpdateEvent>(serialized)

            assertEquals(
                """{"lobbyCode":"1071","playerId":8,"status":"${status.name}"}""",
                serialized,
            )
            assertEquals(event, deserialized)
        }
    }

    @Test
    fun `should reject every missing field during deserialization`() {
        listOf(
            """{"playerId":8,"status":"CONNECTED"}""",
            """{"lobbyCode":"1071","status":"CONNECTED"}""",
            """{"lobbyCode":"1071","playerId":8}""",
        ).forEach { serialized ->
            assertThrows(SerializationException::class.java) {
                json.decodeFromString<ConnectionStatusUpdateEvent>(serialized)
            }
        }
    }

    @Test
    fun `should reject unexpected serializer index`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionStatusUpdateEventSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(99)),
            )
        }
    }

    @Test
    fun `should reject missing fields in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            ConnectionStatusUpdateEventSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }
}
