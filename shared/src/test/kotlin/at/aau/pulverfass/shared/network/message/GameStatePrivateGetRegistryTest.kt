package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameStatePrivateGetRegistryTest {
    @Test
    fun `should resolve message type and serialization for private get request`() {
        val payload = GameStatePrivateGetRequest(LobbyCode("PG12"), PlayerId(4))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_GAME_STATE_PRIVATE_GET_REQUEST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for private get response`() {
        val payload =
            GameStatePrivateGetResponse(
                lobbyCode = LobbyCode("PG34"),
                recipientPlayerId = PlayerId(7),
                stateVersion = 11,
                handCards = listOf("infantry"),
                secretObjectives = listOf("eliminate_blue"),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_GAME_STATE_PRIVATE_GET_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for private get error response`() {
        val payload =
            GameStatePrivateGetErrorResponse(
                code = GameStatePrivateGetErrorCode.NOT_IN_GAME,
                reason = "Spieler '9' ist nicht Teil der Lobby 'PG56'.",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_GAME_STATE_PRIVATE_GET_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }
}
