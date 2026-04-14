package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class GameJoinRequestTest {
    private val json = Json

    @Test
    fun `should create game join request correctly`() {
        val request = GameJoinRequest(LobbyCode("AB12"))

        assertEquals(LobbyCode("AB12"), request.lobbyCode)
    }

    @Test
    fun `should implement network message payload`() {
        val request = GameJoinRequest(LobbyCode("CD34"))
        val payload: NetworkMessagePayload = request

        assertEquals(request, payload)
    }

    @Test
    fun `should serialize and deserialize game join request`() {
        val request = GameJoinRequest(LobbyCode("EF56"))

        val serialized = json.encodeToString(GameJoinRequest.serializer(), request)
        val deserialized = json.decodeFromString<GameJoinRequest>(serialized)

        assertEquals("""{"lobbyCode":"EF56"}""", serialized)
        assertEquals(request, deserialized)
    }

    @Test
    fun `should reject null lobby code at constructor boundary`() {
        val constructor =
            GameJoinRequest::class.java.declaredConstructors.first {
                it.parameterTypes.contentEquals(
                    arrayOf(
                        String::class.java,
                        kotlin.jvm.internal.DefaultConstructorMarker::class.java,
                    ),
                )
            }
        constructor.isAccessible = true
        val valid = constructor.newInstance("GH78", null) as GameJoinRequest

        assertEquals(LobbyCode("GH78"), valid.lobbyCode)

        assertThrows(InvocationTargetException::class.java) {
            constructor.newInstance(null, null)
        }
    }

    @Test
    fun `should reject missing lobby code during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<GameJoinRequest>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            GameJoinRequestSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing field in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            GameJoinRequestSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }

    @Test
    fun `should decode direct serializer path with configured scalar string`() {
        val decoded =
            GameJoinRequestSerializer.deserialize(
                SerializerTestDecoder(
                    indices = intArrayOf(0, CompositeDecoder.DECODE_DONE),
                    scalarString = "ZX90",
                ),
            )

        assertEquals(LobbyCode("ZX90"), decoded.lobbyCode)
    }
}
