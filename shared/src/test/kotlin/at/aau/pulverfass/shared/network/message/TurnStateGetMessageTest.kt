package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TurnStateGetMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request = TurnStateGetRequest(lobbyCode = LobbyCode("AB12"))

        val serialized = json.encodeToString(TurnStateGetRequest.serializer(), request)
        val deserialized = json.decodeFromString(TurnStateGetRequest.serializer(), serialized)

        assertEquals("""{"lobbyCode":"AB12"}""", serialized)
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response =
            TurnStateGetResponse(
                lobbyCode = LobbyCode("CD34"),
                activePlayerId = PlayerId(2),
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 5,
                startPlayerId = PlayerId(1),
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                pausedPlayerId = PlayerId(2),
            )

        val serialized = json.encodeToString(TurnStateGetResponse.serializer(), response)
        val deserialized = json.decodeFromString(TurnStateGetResponse.serializer(), serialized)

        assertTrue(serialized.contains("turnCount"))
        assertTrue(serialized.contains("pauseReason"))
        assertTrue(serialized.contains("pausedPlayerId"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            TurnStateGetErrorResponse(
                code = TurnStateGetErrorCode.GAME_NOT_FOUND,
                reason = "Lobby 'ZZ99' wurde nicht gefunden.",
            )

        val serialized = json.encodeToString(TurnStateGetErrorResponse.serializer(), response)
        val deserialized = json.decodeFromString(TurnStateGetErrorResponse.serializer(), serialized)

        assertEquals(
            """{"code":"GAME_NOT_FOUND","reason":"Lobby 'ZZ99' wurde nicht gefunden."}""",
            serialized,
        )
        assertEquals(response, deserialized)
    }
}
