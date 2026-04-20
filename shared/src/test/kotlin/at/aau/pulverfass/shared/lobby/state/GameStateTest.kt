package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameStateTest {
    @Test
    fun `should expose consistent initial state`() {
        val state = GameState.initial(LobbyCode("AB12"))

        assertEquals(LobbyCode("AB12"), state.lobbyCode)
        assertTrue(state.players.isEmpty())
        assertTrue(state.turnOrder.isEmpty())
        assertNull(state.activePlayer)
        assertEquals(0, state.turnNumber)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, state.status)
        assertEquals(0, state.processedEventCount)
        assertEquals(0, state.playerCount)
    }

    @Test
    fun `should hold players turn and status data consistently when instantiated`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val context = EventContext(occurredAtEpochMillis = 1234)
        val state =
            GameState(
                lobbyCode = LobbyCode("CD34"),
                players = listOf(playerOne, playerTwo),
                activePlayer = playerOne,
                turnOrder = listOf(playerOne, playerTwo),
                turnNumber = 3,
                status = GameStatus.RUNNING,
                processedEventCount = 4,
                lastEventContext = context,
            )

        assertEquals(listOf(playerOne, playerTwo), state.players)
        assertEquals(listOf(playerOne, playerTwo), state.turnOrder)
        assertEquals(playerOne, state.activePlayer)
        assertEquals(3, state.turnNumber)
        assertEquals(GameStatus.RUNNING, state.status)
        assertEquals(context, state.lastEventContext)
        assertNull(state.closedReason)
        assertNull(state.lastInvalidActionReason)
        assertEquals(2, state.playerCount)
        assertTrue(state.hasPlayer(playerTwo))
    }

    @Test
    fun `should keep two game state instances isolated from each other`() {
        val firstPlayer = PlayerId(21)
        val secondPlayer = PlayerId(22)

        val firstState =
            GameState(
                lobbyCode = LobbyCode("GH78"),
                players = listOf(firstPlayer),
                turnOrder = listOf(firstPlayer),
            )
        val secondState =
            GameState(
                lobbyCode = LobbyCode("JK90"),
                players = listOf(secondPlayer),
                turnOrder = listOf(secondPlayer),
            )

        assertEquals(listOf(firstPlayer), firstState.players)
        assertEquals(listOf(secondPlayer), secondState.players)
        assertFalse(firstState.hasPlayer(secondPlayer))
        assertFalse(secondState.hasPlayer(firstPlayer))
    }

    @Test
    fun `should expose optional metadata fields`() {
        val state =
            GameState(
                lobbyCode = LobbyCode("LM12"),
                closedReason = "timeout",
                lastInvalidActionReason = "invalid move",
            )

        assertEquals("timeout", state.closedReason)
        assertEquals("invalid move", state.lastInvalidActionReason)
    }

    @Test
    fun `should reject invalid constructor arguments`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)

        assertThrows(IllegalArgumentException::class.java) {
            GameState(lobbyCode = LobbyCode("NO34"), turnNumber = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(lobbyCode = LobbyCode("PQ56"), processedEventCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("RS78"),
                players = listOf(playerOne, playerOne),
                turnOrder = listOf(playerOne),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("TU90"),
                players = listOf(playerOne),
                turnOrder = listOf(playerOne, playerOne),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("VW12"),
                players = listOf(playerOne),
                turnOrder = listOf(playerTwo),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("ZA56"),
                players = listOf(playerOne, playerTwo),
                turnOrder = listOf(playerOne),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("XY34"),
                players = listOf(playerOne),
                turnOrder = listOf(playerOne),
                activePlayer = playerTwo,
            )
        }
    }
}
