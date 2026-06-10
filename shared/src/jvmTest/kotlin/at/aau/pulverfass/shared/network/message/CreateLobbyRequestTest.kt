package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequestSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CreateLobbyRequestTest {
    private val json = Json

    @Test
    fun `should implement network message payload`() {
        val payload: NetworkMessagePayload = CreateLobbyRequest

        assertEquals(CreateLobbyRequest, payload)
    }

    @Test
    fun `should serialize and deserialize empty create lobby request`() {
        val serialized = json.encodeToString(CreateLobbyRequest.serializer(), CreateLobbyRequest)
        val deserialized = json.decodeFromString<CreateLobbyRequest>(serialized)

        assertEquals("{}", serialized)
        assertEquals(CreateLobbyRequest, deserialized)
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            CreateLobbyRequestSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }
}
