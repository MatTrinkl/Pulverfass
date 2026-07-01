package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequestSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class LeaveLobbyRequestTest {
    private val json = Json

    @Test
    fun `should create leave lobby request correctly`() {
        val request = LeaveLobbyRequest(LobbyCode("1003"))

        assertEquals(LobbyCode("1003"), request.lobbyCode)
    }

    @Test
    fun `should implement network message payload`() {
        val request = LeaveLobbyRequest(LobbyCode("1071"))
        val payload: NetworkMessagePayload = request

        assertEquals(request, payload)
    }

    @Test
    fun `should serialize and deserialize leave lobby request`() {
        val request = LeaveLobbyRequest(LobbyCode("1132"))

        val serialized = json.encodeToString(LeaveLobbyRequest.serializer(), request)
        val deserialized = json.decodeFromString<LeaveLobbyRequest>(serialized)

        assertEquals("""{"lobbyCode":"1132"}""", serialized)
        assertEquals(request, deserialized)
    }

    @Test
    fun `should reject null lobby code at constructor boundary`() {
        val constructor =
            LeaveLobbyRequest::class.java.declaredConstructors.first {
                it.parameterTypes.contentEquals(
                    arrayOf(
                        String::class.java,
                        kotlin.jvm.internal.DefaultConstructorMarker::class.java,
                    ),
                )
            }
        constructor.isAccessible = true
        val valid = constructor.newInstance("1178", null) as LeaveLobbyRequest

        assertEquals(LobbyCode("1178"), valid.lobbyCode)

        assertThrows(InvocationTargetException::class.java) {
            constructor.newInstance(null, null)
        }
    }

    @Test
    fun `should reject missing lobby code during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<LeaveLobbyRequest>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            LeaveLobbyRequestSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing field in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            LeaveLobbyRequestSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }
}
