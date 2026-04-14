package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadTypeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NetworkPayloadRegistryTest {
    @Test
    fun `should resolve message type and serialization for game join request`() {
        val payload = GameJoinRequest(LobbyCode("AB12"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.GAME_JOIN_REQUEST, messageType)
        assertEquals("""{"lobbyCode":"AB12"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for login request`() {
        val payload = LoginRequest(username = "alice", password = "secret")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOGIN_REQUEST, messageType)
        assertEquals("""{"username":"alice","password":"secret"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should reject unsupported payload class`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                NetworkPayloadRegistry.messageTypeFor(UnsupportedPayload)
            }

        assertEquals(
            "Unsupported payload class: ${UnsupportedPayload::class.java.name}",
            exception.message,
        )
    }

    @Test
    fun `should reject unsupported payload class during serialization`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                NetworkPayloadRegistry.serializePayload(UnsupportedPayload)
            }

        assertEquals(
            "Unsupported payload class: ${UnsupportedPayload::class.java.name}",
            exception.message,
        )
    }

    @Test
    fun `should reject unsupported payload type`() {
        val exception =
            assertThrows(UnsupportedPayloadTypeException::class.java) {
                NetworkPayloadRegistry.deserializePayload(
                    MessageType.HEARTBEAT,
                    "{}",
                )
            }

        assertEquals("Unsupported payload type: HEARTBEAT", exception.message)
    }

    private data object UnsupportedPayload : NetworkMessagePayload
}
