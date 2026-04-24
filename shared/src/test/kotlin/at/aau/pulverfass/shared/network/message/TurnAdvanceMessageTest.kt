package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TurnAdvanceMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request with expected phase`() {
        val request =
            TurnAdvanceRequest(
                lobbyCode = LobbyCode("AB12"),
                playerId = PlayerId(7),
                expectedPhase = TurnPhase.ATTACK,
            )

        val serialized = json.encodeToString(TurnAdvanceRequest.serializer(), request)
        val deserialized = json.decodeFromString(TurnAdvanceRequest.serializer(), serialized)

        assertEquals("""{"lobbyCode":"AB12","playerId":7,"expectedPhase":"ATTACK"}""", serialized)
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response = TurnAdvanceResponse(lobbyCode = LobbyCode("CD34"))

        val serialized = json.encodeToString(TurnAdvanceResponse.serializer(), response)
        val deserialized = json.decodeFromString(TurnAdvanceResponse.serializer(), serialized)

        assertEquals("""{"lobbyCode":"CD34"}""", serialized)
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            TurnAdvanceErrorResponse(
                code = TurnAdvanceErrorCode.PHASE_MISMATCH,
                reason = "Erwartete Phase 'ATTACK', aktueller Serverzustand ist 'FORTIFY'.",
            )

        val serialized = json.encodeToString(TurnAdvanceErrorResponse.serializer(), response)
        val deserialized = json.decodeFromString(TurnAdvanceErrorResponse.serializer(), serialized)

        assertTrue(serialized.contains("PHASE_MISMATCH"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip turn state updated broadcast`() {
        val event =
            TurnStateUpdatedEvent(
                lobbyCode = LobbyCode("EF56"),
                activePlayerId = PlayerId(1),
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 3,
                startPlayerId = PlayerId(1),
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                pausedPlayerId = PlayerId(1),
            )

        val serialized = json.encodeToString(TurnStateUpdatedEvent.serializer(), event)
        val deserialized = json.decodeFromString(TurnStateUpdatedEvent.serializer(), serialized)

        assertTrue(serialized.contains("turnPhase"))
        assertTrue(serialized.contains("pauseReason"))
        assertTrue(serialized.contains("pausedPlayerId"))
        assertEquals(event, deserialized)
    }

    @Test
    fun `serializer roundtrip phase boundary broadcast`() {
        val event =
            PhaseBoundaryEvent(
                lobbyCode = LobbyCode("GH78"),
                stateVersion = 12,
                previousPhase = TurnPhase.FORTIFY,
                nextPhase = TurnPhase.DRAW_CARD,
                activePlayerId = PlayerId(2),
                turnCount = 4,
            )

        val serialized = json.encodeToString(PhaseBoundaryEvent.serializer(), event)
        val deserialized = json.decodeFromString(PhaseBoundaryEvent.serializer(), serialized)

        assertTrue(serialized.contains("previousPhase"))
        assertTrue(serialized.contains("stateVersion"))
        assertEquals(event, deserialized)
    }
}
