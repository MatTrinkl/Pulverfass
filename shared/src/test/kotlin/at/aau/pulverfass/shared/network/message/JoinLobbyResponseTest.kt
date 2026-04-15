package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponseSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class JoinLobbyResponseTest {
    private val json = Json

    @Test
    fun `should create join lobby response correctly`() {
        val response = JoinLobbyResponse(LobbyCode("AB12"))

        assertEquals(LobbyCode("AB12"), response.lobbyCode)
    }

    @Test
    fun `should implement network message payload`() {
        val response = JoinLobbyResponse(LobbyCode("CD34"))
        val payload: NetworkMessagePayload = response

        assertEquals(response, payload)
    }

    @Test
    fun `should serialize and deserialize join lobby response`() {
        val response = JoinLobbyResponse(LobbyCode("EF56"))

        val serialized = json.encodeToString(JoinLobbyResponse.serializer(), response)
        val deserialized = json.decodeFromString<JoinLobbyResponse>(serialized)

        assertEquals("""{"lobbyCode":"EF56"}""", serialized)
        assertEquals(response, deserialized)
    }

    @Test
    fun `should reject null lobby code at constructor boundary`() {
        val constructor =
            JoinLobbyResponse::class.java.declaredConstructors.first {
                it.parameterTypes.contentEquals(
                    arrayOf(
                        String::class.java,
                        kotlin.jvm.internal.DefaultConstructorMarker::class.java,
                    ),
                )
            }
        constructor.isAccessible = true
        val valid = constructor.newInstance("GH78", null) as JoinLobbyResponse

        assertEquals(LobbyCode("GH78"), valid.lobbyCode)

        assertThrows(InvocationTargetException::class.java) {
            constructor.newInstance(null, null)
        }
    }

    @Test
    fun `should reject missing lobby code during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<JoinLobbyResponse>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            JoinLobbyResponseSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing field in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            JoinLobbyResponseSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }
}
