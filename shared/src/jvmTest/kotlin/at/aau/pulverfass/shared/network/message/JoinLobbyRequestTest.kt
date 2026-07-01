package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.MAX_PLAYER_DISPLAY_NAME_LENGTH
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequestSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class JoinLobbyRequestTest {
    private val json = Json

    @Test
    fun `should create join lobby request correctly`() {
        val request = JoinLobbyRequest(LobbyCode("1003"), "Alice")

        assertEquals(LobbyCode("1003"), request.lobbyCode)
        assertEquals("Alice", request.playerDisplayName)
    }

    @Test
    fun `should implement network message payload`() {
        val request = JoinLobbyRequest(LobbyCode("1071"), "Bob")
        val payload: NetworkMessagePayload = request

        assertEquals(request, payload)
    }

    @Test
    fun `should serialize and deserialize join lobby request`() {
        val request = JoinLobbyRequest(LobbyCode("1132"), "Carol")

        val serialized = json.encodeToString(JoinLobbyRequest.serializer(), request)
        val deserialized = json.decodeFromString<JoinLobbyRequest>(serialized)

        assertEquals("""{"lobbyCode":"1132","playerDisplayName":"Carol"}""", serialized)
        assertEquals(request, deserialized)
    }

    @Test
    fun `should reject blank player display name`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                JoinLobbyRequest(LobbyCode("1003"), "   ")
            }

        assertTrue(exception.message.orEmpty().contains("playerDisplayName"))
    }

    @Test
    fun `should reject player display name longer than max length`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                JoinLobbyRequest(LobbyCode("1003"), "a".repeat(MAX_PLAYER_DISPLAY_NAME_LENGTH + 1))
            }

        assertTrue(exception.message.orEmpty().contains(MAX_PLAYER_DISPLAY_NAME_LENGTH.toString()))
    }

    @Test
    fun `should reject missing fields at constructor boundary`() {
        val constructor =
            JoinLobbyRequest::class.java.declaredConstructors.first {
                it.parameterTypes.contentEquals(
                    arrayOf(
                        String::class.java,
                        String::class.java,
                        kotlin.jvm.internal.DefaultConstructorMarker::class.java,
                    ),
                )
            }
        constructor.isAccessible = true
        val valid = constructor.newInstance("1178", "Dora", null) as JoinLobbyRequest

        assertEquals(LobbyCode("1178"), valid.lobbyCode)
        assertEquals("Dora", valid.playerDisplayName)

        assertThrows(InvocationTargetException::class.java) {
            constructor.newInstance(null, "Dora", null)
        }
        assertThrows(InvocationTargetException::class.java) {
            constructor.newInstance("1178", null, null)
        }
    }

    @Test
    fun `should reject missing lobby code during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<JoinLobbyRequest>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            JoinLobbyRequestSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing field in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            JoinLobbyRequestSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }
}
