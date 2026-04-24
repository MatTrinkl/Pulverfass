package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
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
                lobbyCode = LobbyCode("AB12"),
                playerId = PlayerId(3),
            )

        val serialized = json.encodeToString(GameStatePrivateGetRequest.serializer(), request)
        val deserialized =
            json.decodeFromString(
                GameStatePrivateGetRequest.serializer(),
                serialized,
            )

        assertEquals("""{"lobbyCode":"AB12","playerId":3}""", serialized)
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response =
            GameStatePrivateGetResponse(
                lobbyCode = LobbyCode("CD34"),
                recipientPlayerId = PlayerId(2),
                stateVersion = 9,
                handCards = listOf("infantry", "cavalry"),
                secretObjectives = listOf("hold_europe"),
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
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            GameStatePrivateGetErrorResponse(
                code = GameStatePrivateGetErrorCode.REQUESTER_MISMATCH,
                reason = "Requester '2' passt nicht zur aktuellen Connection '1'.",
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
}
