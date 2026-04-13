package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
}
