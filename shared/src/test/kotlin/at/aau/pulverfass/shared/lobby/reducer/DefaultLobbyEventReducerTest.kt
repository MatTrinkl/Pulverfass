package at.aau.pulverfass.shared.lobby.reducer

import at.aau.pulverfass.shared.event.CorrelationId
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.PlayerLeft
import at.aau.pulverfass.shared.lobby.event.TurnEnded
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DefaultLobbyEventReducerTest {
    private val reducer = DefaultLobbyEventReducer()

    @Test
    fun `player joined verändert state korrekt`() {
        val lobbyCode = LobbyCode("AB12")
        val playerId = PlayerId(1)
        val context =
            EventContext(
                connectionId = ConnectionId(5),
                occurredAtEpochMillis = 100,
                correlationId = CorrelationId("join-1"),
            )

        val updatedState =
            reducer.apply(
                state = GameState.initial(lobbyCode),
                event = PlayerJoined(lobbyCode, playerId),
                context = context,
            )

        assertEquals(listOf(playerId), updatedState.players)
        assertEquals(listOf(playerId), updatedState.turnOrder)
        assertEquals(playerId, updatedState.activePlayer)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, updatedState.status)
        assertEquals(1, updatedState.processedEventCount)
        assertEquals(context, updatedState.lastEventContext)
    }

    @Test
    fun `turn ended verändert turn information korrekt`() {
        val lobbyCode = LobbyCode("CD34")
        val firstPlayer = PlayerId(1)
        val secondPlayer = PlayerId(2)
        val runningState =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(firstPlayer, secondPlayer),
                activePlayer = firstPlayer,
                turnOrder = listOf(firstPlayer, secondPlayer),
                status = GameStatus.RUNNING,
            )

        val updatedState = reducer.apply(runningState, TurnEnded(lobbyCode, firstPlayer))

        assertEquals(secondPlayer, updatedState.activePlayer)
        assertEquals(1, updatedState.turnNumber)
        assertEquals(GameStatus.RUNNING, updatedState.status)
    }

    @Test
    fun `ungueltige aktion wird erkannt`() {
        val lobbyCode = LobbyCode("EF56")
        val state = GameState.initial(lobbyCode)

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(state, TurnEnded(lobbyCode, PlayerId(9)))
        }

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(state, PlayerLeft(lobbyCode, PlayerId(9)))
        }
    }

    @Test
    fun `reducer arbeitet deterministisch`() {
        val lobbyCode = LobbyCode("GH78")
        val playerId = PlayerId(7)
        val state = GameState.initial(lobbyCode)
        val event = PlayerJoined(lobbyCode, playerId)
        val context =
            EventContext(
                connectionId = ConnectionId(8),
                playerId = playerId,
                occurredAtEpochMillis = 500,
                correlationId = CorrelationId("det-1"),
            )

        val firstResult = reducer.apply(state, event, context)
        val secondResult = reducer.apply(state, event, context)

        assertEquals(firstResult, secondResult)
    }
}
