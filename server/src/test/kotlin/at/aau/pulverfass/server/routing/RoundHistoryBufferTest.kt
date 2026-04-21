package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoundHistoryBufferTest {
    @Test
    fun `eviction policy keeps only last two rounds`() {
        val buffer = RoundHistoryBuffer(maxRounds = 2)

        buffer.recordDelta(roundIndex = 1, fromVersion = 1, toVersion = 1, eventCount = 1)
        buffer.recordDelta(roundIndex = 2, fromVersion = 2, toVersion = 2, eventCount = 1)
        buffer.recordDelta(roundIndex = 3, fromVersion = 3, toVersion = 3, eventCount = 1)

        assertEquals(listOf(2, 3), buffer.history().map(RoundHistory::roundIndex))
    }

    @Test
    fun `buffer tracks state version boundaries across all record types`() {
        val lobbyCode = LobbyCode("RH01")
        val playerId = PlayerId(7)
        val buffer = RoundHistoryBuffer(maxRounds = 2)

        buffer.recordDelta(roundIndex = 2, fromVersion = 5, toVersion = 6, eventCount = 2)
        buffer.recordBoundary(
            PhaseBoundaryEvent(
                lobbyCode = lobbyCode,
                stateVersion = 7,
                previousPhase = TurnPhase.ATTACK,
                nextPhase = TurnPhase.FORTIFY,
                activePlayerId = playerId,
                turnCount = 2,
            ),
        )
        buffer.recordTurnStateChange(
            stateVersion = 8,
            event =
                TurnStateUpdatedEvent(
                    lobbyCode = lobbyCode,
                    activePlayerId = playerId,
                    turnPhase = TurnPhase.FORTIFY,
                    turnCount = 2,
                    startPlayerId = playerId,
                ),
        )
        buffer.recordSnapshot(
            roundIndex = 2,
            stateVersion = 9,
            trigger = RoundSnapshotTrigger.TURN_CHANGE_BROADCAST,
        )

        val history = buffer.history().single()
        assertEquals(2, history.roundIndex)
        assertEquals(5, history.startStateVersion)
        assertEquals(9, history.endStateVersion)
        assertEquals(1, history.deltas.size)
        assertEquals(1, history.phaseBoundaries.size)
        assertEquals(1, history.turnStateChanges.size)
        assertEquals(1, history.snapshots.size)
        assertEquals(TurnPhase.FORTIFY, history.turnStateChanges.single().turnPhase)
    }

    @Test
    fun `describe exposes compact observability summary`() {
        val buffer = RoundHistoryBuffer(maxRounds = 2)

        buffer.recordDelta(roundIndex = 4, fromVersion = 11, toVersion = 12, eventCount = 2)
        buffer.recordSnapshot(
            roundIndex = 4,
            stateVersion = 12,
            trigger = RoundSnapshotTrigger.CATCH_UP_RESPONSE,
        )

        val description = buffer.describe()

        assertTrue(description.contains("round=4"))
        assertTrue(description.contains("versions=11..12"))
        assertTrue(description.contains("deltas=1"))
        assertTrue(description.contains("snapshots=1"))
    }
}
