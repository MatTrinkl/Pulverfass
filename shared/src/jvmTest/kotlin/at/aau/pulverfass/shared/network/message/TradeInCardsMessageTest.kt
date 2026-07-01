package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import at.aau.pulverfass.shared.message.lobby.request.TradeInCardsRequest
import at.aau.pulverfass.shared.message.lobby.response.TradeInCardsResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TradeInCardsMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request =
            TradeInCardsRequest(
                lobbyCode = LobbyCode("1003"),
                playerId = PlayerId(7),
                cardIds = listOf(CardId("card-a"), CardId("card-b"), CardId("card-c")),
            )

        val serialized = json.encodeToString(TradeInCardsRequest.serializer(), request)
        val deserialized = json.decodeFromString(TradeInCardsRequest.serializer(), serialized)

        assertTrue(serialized.contains("cardIds"))
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response = TradeInCardsResponse(lobbyCode = LobbyCode("1071"))

        val serialized = json.encodeToString(TradeInCardsResponse.serializer(), response)
        val deserialized = json.decodeFromString(TradeInCardsResponse.serializer(), serialized)

        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            TradeInCardsErrorResponse(
                code = TradeInCardsErrorCode.INVALID_SET,
                reason = "Die ausgewaehlten Karten bilden kein gueltiges Trade-In-Set.",
            )

        val serialized = json.encodeToString(TradeInCardsErrorResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(TradeInCardsErrorResponse.serializer(), serialized)

        assertTrue(serialized.contains("INVALID_SET"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip private hand updated event`() {
        val event =
            PlayerHandUpdatedEvent(
                lobbyCode = LobbyCode("1132"),
                recipientPlayerId = PlayerId(4),
                stateVersion = 9,
                handCards =
                    listOf(
                        PrivateHandCardSnapshot(CardId("card-a"), CardType.A),
                        PrivateHandCardSnapshot(CardId("card-joker"), CardType.JOKER),
                    ),
            )

        val serialized = json.encodeToString(PlayerHandUpdatedEvent.serializer(), event)
        val deserialized =
            json.decodeFromString(PlayerHandUpdatedEvent.serializer(), serialized)

        assertTrue(serialized.contains("recipientPlayerId"))
        assertTrue(serialized.contains("handCards"))
        assertEquals(event, deserialized)
    }
}
