package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadTypeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NetworkPayloadRegistryTest {
    @Test
    fun `should resolve message type and serialization for connection response`() {
        val payload = ConnectionResponse(SessionToken("123e4567-e89b-12d3-a456-426614174101"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.CONNECTION_RESPONSE, messageType)
        assertEquals(
            """{"sessionToken":"123e4567-e89b-12d3-a456-426614174101"}""",
            serialized,
        )
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for create lobby request`() {
        val payload = CreateLobbyRequest

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CREATE_REQUEST, messageType)
        assertEquals("{}", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for create lobby error response`() {
        val payload = CreateLobbyErrorResponse(reason = "Lobby konnte nicht erstellt werden.")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CREATE_ERROR_RESPONSE, messageType)
        assertEquals("""{"reason":"Lobby konnte nicht erstellt werden."}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for create lobby response`() {
        val payload = CreateLobbyResponse(LobbyCode("AB12"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CREATE_RESPONSE, messageType)
        assertEquals("""{"lobbyCode":"AB12"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for join lobby request`() {
        val payload = JoinLobbyRequest(LobbyCode("AB12"), "Alice")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_JOIN_REQUEST, messageType)
        assertEquals("""{"lobbyCode":"AB12","playerDisplayName":"Alice"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for join lobby response`() {
        val payload = JoinLobbyResponse(LobbyCode("CD34"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_JOIN_RESPONSE, messageType)
        assertEquals("""{"lobbyCode":"CD34"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for join lobby error response`() {
        val payload = JoinLobbyErrorResponse(reason = "Lobby wurde nicht gefunden.")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_JOIN_ERROR_RESPONSE, messageType)
        assertEquals("""{"reason":"Lobby wurde nicht gefunden."}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for player joined lobby event`() {
        val payload =
            PlayerJoinedLobbyEvent(
                LobbyCode("EF56"),
                PlayerId(8),
                "Bob",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLAYER_JOINED_BROADCAST, messageType)
        assertEquals(
            """{"lobbyCode":"EF56","playerId":8,"playerDisplayName":"Bob"}""",
            serialized,
        )
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for leave lobby request`() {
        val payload = LeaveLobbyRequest(LobbyCode("GH78"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_LEAVE_REQUEST, messageType)
        assertEquals("""{"lobbyCode":"GH78"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for leave lobby response`() {
        val payload = LeaveLobbyResponse(LobbyCode("IJ90"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_LEAVE_RESPONSE, messageType)
        assertEquals("""{"lobbyCode":"IJ90"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for player left lobby event`() {
        val payload = PlayerLeftLobbyEvent(LobbyCode("KL12"), PlayerId(9))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLAYER_LEFT_BROADCAST, messageType)
        assertEquals("""{"lobbyCode":"KL12","playerId":9}""", serialized)
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
