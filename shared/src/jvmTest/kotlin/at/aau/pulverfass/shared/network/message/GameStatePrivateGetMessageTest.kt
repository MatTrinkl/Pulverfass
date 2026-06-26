package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.HandState
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameStatePrivateGetMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request =
            GameStatePrivateGetRequest(
                lobbyCode = LobbyCode("1003"),
                playerId = PlayerId(3),
            )

        val serialized = json.encodeToString(GameStatePrivateGetRequest.serializer(), request)
        val deserialized =
            json.decodeFromString(
                GameStatePrivateGetRequest.serializer(),
                serialized,
            )

        assertEquals("""{"lobbyCode":"1003","playerId":3}""", serialized)
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response =
            GameStatePrivateGetResponse(
                lobbyCode = LobbyCode("1071"),
                recipientPlayerId = PlayerId(2),
                stateVersion = 9,
                handCards = listOf("infantry", "cavalry"),
                secretObjectives = listOf("hold_europe"),
                privateHandCards =
                    listOf(
                        PrivateHandCardSnapshot(CardId("card-a"), CardType.A),
                    ),
            )

        val serialized = json.encodeToString(GameStatePrivateGetResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(
                GameStatePrivateGetResponse.serializer(),
                serialized,
            )

        assertTrue(serialized.contains("recipientPlayerId"))
        assertTrue(serialized.contains("stateVersion"))
        assertTrue(serialized.contains("handCards"))
        assertTrue(serialized.contains("privateHandCards"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            GameStatePrivateGetErrorResponse(
                code = GameStatePrivateGetErrorCode.PAYLOAD_TOO_LARGE,
                reason = "Privater Snapshot fuer Lobby '1003' ist groesser als 128 Bytes.",
            )

        val serialized =
            json.encodeToString(
                GameStatePrivateGetErrorResponse.serializer(),
                response,
            )
        val deserialized =
            json.decodeFromString(
                GameStatePrivateGetErrorResponse.serializer(),
                serialized,
            )

        assertEquals(response, deserialized)
    }

    @Test
    fun `response from game state exposes only recipient typed hand`() {
        val recipient = PlayerId(2)
        val card = CardState(CardId("card-private"), CardType.JOKER)
        val gameState =
            GameState(
                lobbyCode = LobbyCode("1177"),
                players = listOf(recipient),
                turnOrder = listOf(recipient),
                stateVersion = 11,
                handState = HandState(mapOf(recipient to listOf(card))),
            )

        val response = GameStatePrivateGetResponse.fromGameState(gameState, recipient)

        assertEquals(recipient, response.recipientPlayerId)
        assertEquals(11, response.stateVersion)
        assertEquals(
            listOf(PrivateHandCardSnapshot(card.cardId, card.type)),
            response.privateHandCards,
        )
    }
}
