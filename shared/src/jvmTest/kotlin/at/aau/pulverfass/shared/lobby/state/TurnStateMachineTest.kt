package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TurnStateMachineTest {
    private val playerOne = PlayerId(1)
    private val playerTwo = PlayerId(2)
    private val playerThree = PlayerId(3)

    @Test
    fun `initialize normalizes turn order and selects first player`() {
        val state = TurnStateMachine.initialize(listOf(playerOne, playerOne, playerTwo))

        assertEquals(playerOne, state?.activePlayerId)
        assertEquals(playerOne, state?.startPlayerId)
        assertEquals(TurnPhase.REINFORCEMENTS, state?.turnPhase)
        assertEquals(1, state?.turnCount)
    }

    @Test
    fun `prepare setup state honors preferred player when present`() {
        val state =
            TurnStateMachine.prepareSetupState(
                turnOrder = listOf(playerOne, playerTwo),
                preferredStartPlayerId = playerTwo,
            )

        assertEquals(playerTwo, state?.activePlayerId)
        assertEquals(playerTwo, state?.startPlayerId)
    }

    @Test
    fun `from legacy clamps turn count and falls back to first active player`() {
        val state =
            TurnStateMachine.fromLegacy(
                activePlayer = null,
                turnOrder = listOf(playerOne, playerTwo),
                turnNumber = -3,
            )

        assertEquals(playerOne, state?.activePlayerId)
        assertEquals(playerOne, state?.startPlayerId)
        assertEquals(1, state?.turnCount)
    }

    @Test
    fun `advance moves through phases and increments round after start player returns`() {
        val order = listOf(playerOne, playerTwo)
        val firstDraw =
            TurnState(
                activePlayerId = playerOne,
                turnPhase = TurnPhase.DRAW_CARD,
                turnCount = 1,
                startPlayerId = playerOne,
            )
        val secondDraw =
            TurnState(
                activePlayerId = playerTwo,
                turnPhase = TurnPhase.DRAW_CARD,
                turnCount = 1,
                startPlayerId = playerOne,
            )

        assertEquals(
            TurnPhase.ATTACK,
            TurnStateMachine
                .advance(firstDraw.copy(turnPhase = TurnPhase.REINFORCEMENTS), order)
                .turnPhase,
        )
        assertEquals(playerTwo, TurnStateMachine.advance(firstDraw, order).activePlayerId)

        val nextRound = TurnStateMachine.advance(secondDraw, order)
        assertEquals(playerOne, nextRound.activePlayerId)
        assertEquals(TurnPhase.REINFORCEMENTS, nextRound.turnPhase)
        assertEquals(2, nextRound.turnCount)
    }

    @Test
    fun `advance rejects paused or stale turn states`() {
        val paused =
            TurnState(
                activePlayerId = playerOne,
                turnPhase = TurnPhase.ATTACK,
                startPlayerId = playerOne,
                isPaused = true,
                pauseReason = "manual",
            )
        val unknownActive =
            TurnState(playerThree, TurnPhase.ATTACK, startPlayerId = playerOne)
        val unknownStart =
            TurnState(playerOne, TurnPhase.ATTACK, startPlayerId = playerThree)

        assertThrows(IllegalArgumentException::class.java) {
            TurnStateMachine.advance(paused, listOf(playerOne, playerTwo))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnStateMachine.advance(unknownActive, listOf(playerOne, playerTwo))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnStateMachine.advance(unknownStart, listOf(playerOne, playerTwo))
        }
    }

    @Test
    fun `turn state constructor rejects inconsistent pause state`() {
        assertThrows(IllegalArgumentException::class.java) {
            TurnState(playerOne, TurnPhase.ATTACK, turnCount = 0, startPlayerId = playerOne)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnState(
                activePlayerId = playerOne,
                turnPhase = TurnPhase.ATTACK,
                startPlayerId = playerOne,
                isPaused = true,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnState(
                activePlayerId = playerOne,
                turnPhase = TurnPhase.ATTACK,
                startPlayerId = playerOne,
                pauseReason = "manual",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnState(
                activePlayerId = playerOne,
                turnPhase = TurnPhase.ATTACK,
                startPlayerId = playerOne,
                pausedPlayerId = playerOne,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnState(
                activePlayerId = playerOne,
                turnPhase = TurnPhase.ATTACK,
                startPlayerId = playerOne,
                isPaused = true,
                pauseReason = "manual",
                pausedPlayerId = playerOne,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnState(
                activePlayerId = playerOne,
                turnPhase = TurnPhase.ATTACK,
                startPlayerId = playerOne,
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnState(
                activePlayerId = playerOne,
                turnPhase = TurnPhase.ATTACK,
                startPlayerId = playerOne,
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                pausedPlayerId = playerTwo,
            )
        }
    }

    @Test
    fun `synchronize removes missing active player and clears invalid wait pause`() {
        val pausedForRemovedPlayer =
            TurnState(
                activePlayerId = playerTwo,
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 2,
                startPlayerId = playerTwo,
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                pausedPlayerId = playerTwo,
            )

        val synchronized =
            TurnStateMachine.synchronizeWithPlayers(
                turnState = pausedForRemovedPlayer,
                turnOrder = listOf(playerOne, playerThree),
                previousTurnOrder = listOf(playerOne, playerTwo, playerThree),
                removedPlayerId = playerTwo,
            )

        assertEquals(playerThree, synchronized?.activePlayerId)
        assertEquals(playerOne, synchronized?.startPlayerId)
        assertEquals(TurnPhase.REINFORCEMENTS, synchronized?.turnPhase)
        assertEquals(false, synchronized?.isPaused)
        assertNull(synchronized?.pauseReason)
        assertNull(synchronized?.pausedPlayerId)
    }

    @Test
    fun `synchronize handles empty and missing turn state inputs`() {
        assertNull(
            TurnStateMachine.synchronizeWithPlayers(
                turnState = TurnState(playerOne, TurnPhase.ATTACK, startPlayerId = playerOne),
                turnOrder = emptyList(),
            ),
        )

        val initialized =
            TurnStateMachine.synchronizeWithPlayers(
                turnState = null,
                turnOrder = listOf(playerTwo),
            )

        assertEquals(playerTwo, initialized?.activePlayerId)
    }
}
