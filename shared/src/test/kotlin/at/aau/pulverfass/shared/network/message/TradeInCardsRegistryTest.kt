package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import at.aau.pulverfass.shared.message.lobby.request.TradeInCardsRequest
import at.aau.pulverfass.shared.message.lobby.response.TradeInCardsResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorResponse
import at.aau.pulverfass.shared.message.protocol.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TradeInCardsRegistryTest {
    @Test
    fun `should resolve message type and serialization for trade in request`() {
        val payload =
            TradeInCardsRequest(
                lobbyCode = LobbyCode("TI12"),
                playerId = PlayerId(4),
                cardIds = listOf(CardId("card-a"), CardId("card-b"), CardId("card-c")),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TRADE_IN_CARDS_REQUEST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for trade in response`() {
        val payload = TradeInCardsResponse(LobbyCode("TI34"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TRADE_IN_CARDS_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for trade in error response`() {
        val payload =
            TradeInCardsErrorResponse(
                code = TradeInCardsErrorCode.CARDS_NOT_OWNED,
                reason = "Alle eingetauschten Karten muessen Spieler '4' gehoeren.",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TRADE_IN_CARDS_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for player hand updated event`() {
        val payload =
            PlayerHandUpdatedEvent(
                lobbyCode = LobbyCode("TI56"),
                recipientPlayerId = PlayerId(4),
                stateVersion = 7,
                handCards = listOf(PrivateHandCardSnapshot(CardId("card-j"), CardType.JOKER)),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLAYER_HAND_UPDATED_EVENT, messageType)
        assertEquals(payload, deserialized)
    }
}
