package at.aau.pulverfass.shared.lobby.reducer

import at.aau.pulverfass.shared.event.CorrelationId
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.PlayerKicked
import at.aau.pulverfass.shared.lobby.event.PlayerLeft
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.event.TimeoutTriggered
import at.aau.pulverfass.shared.lobby.event.TurnEnded
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
                event = PlayerJoined(lobbyCode, playerId, "Alice"),
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
    fun `ungültige aktion wird erkannt`() {
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
        val event = PlayerJoined(lobbyCode, playerId, "Grace")
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

    @Test
    fun `lobby code mismatch wird erkannt`() {
        val reducerAsInterface: LobbyEventReducer = reducer
        val expectedLobbyCode = LobbyCode("AB12")
        val actualLobbyCode = LobbyCode("CD34")
        val state = GameState.initial(expectedLobbyCode)

        val exception =
            assertThrows(LobbyCodeMismatchException::class.java) {
                reducerAsInterface.apply(
                    state = state,
                    event = PlayerJoined(actualLobbyCode, PlayerId(1), "Alice"),
                )
            }

        assertEquals(
            "Reducer f\u00FCr Lobby '$expectedLobbyCode' kann kein Event f\u00FCr " +
                "'$actualLobbyCode' verarbeiten.",
            exception.message,
        )
    }

    @Test
    fun `internal events update metadata without changing turn flow`() {
        val lobbyCode = LobbyCode("IJ90")
        val baseState = GameState.initial(lobbyCode).copy(closedReason = "old")

        val created = reducer.apply(baseState, LobbyCreated(lobbyCode))
        val invalidAction =
            reducer.apply(
                baseState,
                InvalidActionDetected(lobbyCode, reason = "bad"),
            )
        val closed = reducer.apply(baseState, LobbyClosed(lobbyCode, "done"))
        val ticked = reducer.apply(baseState, SystemTick(lobbyCode, 1))
        val timedOut = reducer.apply(baseState, TimeoutTriggered(lobbyCode, "turn", 1_000))

        assertEquals(GameStatus.WAITING_FOR_PLAYERS, created.status)
        assertNull(created.closedReason)
        assertEquals("bad", invalidAction.lastInvalidActionReason)
        assertEquals(GameStatus.CLOSED, closed.status)
        assertEquals("done", closed.closedReason)
        assertNull(closed.activePlayer)
        assertEquals(baseState.players, ticked.players)
        assertEquals(baseState.turnOrder, timedOut.turnOrder)
        assertEquals(1, ticked.processedEventCount)
        assertEquals(1, timedOut.processedEventCount)
    }

    @Test
    fun `player joined handles duplicate and status transitions`() {
        val lobbyCode = LobbyCode("KL12")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)

        val duplicateState =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne),
                turnOrder = listOf(playerOne),
                activePlayer = playerOne,
            )
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(duplicateState, PlayerJoined(lobbyCode, playerOne, "Player 1"))
        }

        val runningState =
            reducer.apply(
                duplicateState,
                PlayerJoined(lobbyCode, playerTwo, "Player 2"),
            )
        assertEquals(GameStatus.RUNNING, runningState.status)
        assertEquals(playerOne, runningState.activePlayer)

        val closedState = duplicateState.copy(status = GameStatus.CLOSED)
        val finishedState = duplicateState.copy(status = GameStatus.FINISHED)

        assertEquals(
            GameStatus.CLOSED,
            reducer.apply(closedState, PlayerJoined(lobbyCode, playerTwo, "Player 2")).status,
        )
        assertEquals(
            GameStatus.FINISHED,
            reducer.apply(finishedState, PlayerJoined(lobbyCode, playerTwo, "Player 2")).status,
        )
    }

    @Test
    fun `player left handles active player and status transitions`() {
        val lobbyCode = LobbyCode("MN34")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val playerThree = PlayerId(3)
        val baseState =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne, playerTwo, playerThree),
                turnOrder = listOf(playerOne, playerTwo, playerThree),
                activePlayer = playerTwo,
                status = GameStatus.RUNNING,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(baseState, PlayerLeft(lobbyCode, PlayerId(99)))
        }

        val removingNonActive = reducer.apply(baseState, PlayerLeft(lobbyCode, playerThree))
        assertEquals(playerTwo, removingNonActive.activePlayer)
        assertEquals(GameStatus.RUNNING, removingNonActive.status)

        val noActivePlayerState = baseState.copy(activePlayer = null)
        val removingWithoutActive =
            reducer.apply(
                noActivePlayerState,
                PlayerLeft(lobbyCode, playerThree),
            )
        assertNull(removingWithoutActive.activePlayer)

        val removingActive = reducer.apply(baseState, PlayerLeft(lobbyCode, playerTwo))
        assertEquals(playerOne, removingActive.activePlayer)
        assertEquals(listOf(playerOne, playerThree), removingActive.turnOrder)

        val singlePlayerState =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne),
                turnOrder = listOf(playerOne),
                activePlayer = playerOne,
            )
        val emptied = reducer.apply(singlePlayerState, PlayerLeft(lobbyCode, playerOne))
        assertNull(emptied.activePlayer)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, emptied.status)

        val closedState = baseState.copy(status = GameStatus.CLOSED)
        val finishedState = baseState.copy(status = GameStatus.FINISHED)
        assertEquals(
            GameStatus.CLOSED,
            reducer.apply(closedState, PlayerLeft(lobbyCode, playerThree)).status,
        )
        assertEquals(
            GameStatus.FINISHED,
            reducer.apply(finishedState, PlayerLeft(lobbyCode, playerThree)).status,
        )
    }

    @Test
    fun `turn ended validates active player`() {
        val lobbyCode = LobbyCode("OP56")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val runningState =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne, playerTwo),
                turnOrder = listOf(playerOne, playerTwo),
                activePlayer = playerOne,
                status = GameStatus.RUNNING,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                runningState.copy(activePlayer = playerTwo),
                TurnEnded(lobbyCode, playerOne),
            )
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(runningState.copy(activePlayer = null), TurnEnded(lobbyCode, playerOne))
        }
    }

    @Test
    fun `interface default implementation applies null context`() {
        val lobbyCode = LobbyCode("QR78")
        val reducerClass =
            Class.forName(
                "at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer\$DefaultImpls",
            )
        val method =
            reducerClass.getDeclaredMethod(
                "apply\$default",
                LobbyEventReducer::class.java,
                GameState::class.java,
                at.aau.pulverfass.shared.lobby.event.LobbyEvent::class.java,
                EventContext::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )

        val updated =
            method.invoke(
                null,
                reducer,
                GameState.initial(lobbyCode),
                PlayerJoined(lobbyCode, PlayerId(3), "Player 3"),
                null,
                4,
                null,
            ) as GameState

        assertEquals(PlayerId(3), updated.activePlayer)
        assertNull(updated.lastEventContext)
    }

    @Test
    fun `player kicked requires owner permission`() {
        val lobbyCode = LobbyCode("ST90")
        val owner = PlayerId(1)
        val targetPlayer = PlayerId(2)
        val nonOwner = PlayerId(3)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, targetPlayer, nonOwner),
                turnOrder = listOf(owner, targetPlayer, nonOwner),
                activePlayer = owner,
                status = GameStatus.RUNNING,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                stateWithOwner,
                PlayerKicked(lobbyCode, targetPlayer, nonOwner),
            )
        }
    }

    @Test
    fun `player kicked validates target player exists`() {
        val lobbyCode = LobbyCode("UV12")
        val owner = PlayerId(1)
        val nonExistentPlayer = PlayerId(99)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner),
                turnOrder = listOf(owner),
                activePlayer = owner,
                status = GameStatus.RUNNING,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                stateWithOwner,
                PlayerKicked(lobbyCode, nonExistentPlayer, owner),
            )
        }
    }

    @Test
    fun `player kicked removes player and updates state correctly`() {
        val lobbyCode = LobbyCode("WX34")
        val owner = PlayerId(1)
        val targetPlayer = PlayerId(2)
        val thirdPlayer = PlayerId(3)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, targetPlayer, thirdPlayer),
                turnOrder = listOf(owner, targetPlayer, thirdPlayer),
                activePlayer = targetPlayer,
                status = GameStatus.RUNNING,
            )

        val updated = reducer.apply(stateWithOwner, PlayerKicked(lobbyCode, targetPlayer, owner))

        assertEquals(listOf(owner, thirdPlayer), updated.players)
        assertEquals(listOf(owner, thirdPlayer), updated.turnOrder)
        assertEquals(thirdPlayer, updated.activePlayer)
        assertEquals(GameStatus.RUNNING, updated.status)
    }

    @Test
    fun `player kicked handles single player removal`() {
        val lobbyCode = LobbyCode("YZ56")
        val owner = PlayerId(1)
        val targetPlayer = PlayerId(2)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, targetPlayer),
                turnOrder = listOf(owner, targetPlayer),
                activePlayer = targetPlayer,
                status = GameStatus.RUNNING,
            )

        val updated = reducer.apply(stateWithOwner, PlayerKicked(lobbyCode, targetPlayer, owner))

        assertEquals(listOf(owner), updated.players)
        assertEquals(listOf(owner), updated.turnOrder)
        assertEquals(owner, updated.activePlayer)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, updated.status)
    }

    @Test
    fun `player kicked transitions status when below 2 players`() {
        val lobbyCode = LobbyCode("AB78")
        val owner = PlayerId(1)
        val targetPlayer = PlayerId(2)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, targetPlayer),
                turnOrder = listOf(owner, targetPlayer),
                activePlayer = owner,
                status = GameStatus.RUNNING,
            )

        val updated = reducer.apply(stateWithOwner, PlayerKicked(lobbyCode, targetPlayer, owner))

        assertEquals(listOf(owner), updated.players)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, updated.status)
    }

    @Test
    fun `game started transitions status to running`() {
        val lobbyCode = LobbyCode("GS01")
        val owner = PlayerId(1)
        val player2 = PlayerId(2)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, player2),
                turnOrder = listOf(owner, player2),
                activePlayer = owner,
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

        val started = reducer.apply(stateWithOwner, GameStarted(lobbyCode))

        assertEquals(GameStatus.RUNNING, started.status)
    }

    @Test
    fun `game started requires minimum 2 players`() {
        val lobbyCode = LobbyCode("GS02")
        val owner = PlayerId(1)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner),
                turnOrder = listOf(owner),
                activePlayer = owner,
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(stateWithOwner, GameStarted(lobbyCode))
        }
    }

    @Test
    fun `game started requires waiting for players status`() {
        val lobbyCode = LobbyCode("GS03")
        val owner = PlayerId(1)
        val player2 = PlayerId(2)
        val stateAlreadyRunning =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, player2),
                turnOrder = listOf(owner, player2),
                activePlayer = owner,
                status = GameStatus.RUNNING,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(stateAlreadyRunning, GameStarted(lobbyCode))
        }
    }
}
