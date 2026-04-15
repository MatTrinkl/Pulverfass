package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponseSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class CreateLobbyResponseTest {
    private val json = Json

    @Test
    fun `should create create lobby response correctly`() {
        val response = CreateLobbyResponse(lobbyCode = LobbyCode("AB12"))

        assertEquals(LobbyCode("AB12"), response.lobbyCode)
    }

    @Test
    fun `should implement network message payload`() {
        val response = CreateLobbyResponse(lobbyCode = LobbyCode("CD34"))
        val payload: NetworkMessagePayload = response

        assertEquals(response, payload)
    }

    @Test
    fun `should serialize and deserialize create lobby response`() {
        val response = CreateLobbyResponse(lobbyCode = LobbyCode("EF56"))

        val serialized = json.encodeToString(CreateLobbyResponse.serializer(), response)
        val deserialized = json.decodeFromString<CreateLobbyResponse>(serialized)

        assertEquals("""{"lobbyCode":"EF56"}""", serialized)
        assertEquals(response, deserialized)
    }

    @Test
    fun `should reject null lobby code at constructor boundary`() {
        val constructor =
            CreateLobbyResponse::class.java.declaredConstructors.first {
                it.parameterTypes.contentEquals(
                    arrayOf(
                        String::class.java,
                        kotlin.jvm.internal.DefaultConstructorMarker::class.java,
                    ),
                )
            }
        constructor.isAccessible = true
        val valid = constructor.newInstance("GH78", null) as CreateLobbyResponse

        assertEquals(LobbyCode("GH78"), valid.lobbyCode)

        assertThrows(InvocationTargetException::class.java) {
            constructor.newInstance(null, null)
        }
    }

    @Test
    fun `should reject missing lobby code during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<CreateLobbyResponse>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            CreateLobbyResponseSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing field in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            CreateLobbyResponseSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }

    @Test
    fun `should decode direct serializer path with configured scalar string`() {
        val decoded =
            CreateLobbyResponseSerializer.deserialize(
                SerializerTestDecoder(
                    indices = intArrayOf(0, CompositeDecoder.DECODE_DONE),
                    scalarString = "ZX90",
                ),
            )

        assertEquals(LobbyCode("ZX90"), decoded.lobbyCode)
    }
}
