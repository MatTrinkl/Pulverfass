package at.aau.pulverfass.shared.lobby.reducer

import at.aau.pulverfass.shared.event.CorrelationId
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.PlayerKicked
import at.aau.pulverfass.shared.lobby.event.PlayerLeft
import at.aau.pulverfass.shared.lobby.event.StartPlayerConfigured
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TimeoutTriggered
import at.aau.pulverfass.shared.lobby.event.TurnEnded
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import kotlin.random.Random
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
        assertEquals(TurnPhase.REINFORCEMENTS, updatedState.turnState?.turnPhase)
        assertEquals(1, updatedState.turnState?.turnCount)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, updatedState.status)
        assertEquals(1, updatedState.stateVersion)
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
                turnState =
                    TurnState(
                        activePlayerId = firstPlayer,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = firstPlayer,
                    ),
                status = GameStatus.RUNNING,
            )

        val afterAttack = reducer.apply(runningState, TurnEnded(lobbyCode, firstPlayer))
        val afterFortify = reducer.apply(afterAttack, TurnEnded(lobbyCode, firstPlayer))
        val afterDrawCard = reducer.apply(afterFortify, TurnEnded(lobbyCode, firstPlayer))
        val switchedPlayer = reducer.apply(afterDrawCard, TurnEnded(lobbyCode, firstPlayer))

        assertEquals(firstPlayer, afterAttack.activePlayer)
        assertEquals(TurnPhase.ATTACK, afterAttack.turnState?.turnPhase)
        assertEquals(TurnPhase.FORTIFY, afterFortify.turnState?.turnPhase)
        assertEquals(TurnPhase.DRAW_CARD, afterDrawCard.turnState?.turnPhase)
        assertEquals(secondPlayer, switchedPlayer.activePlayer)
        assertEquals(TurnPhase.REINFORCEMENTS, switchedPlayer.turnState?.turnPhase)
        assertEquals(1, switchedPlayer.turnState?.turnCount)
        assertEquals(1, switchedPlayer.turnNumber)
        assertEquals(GameStatus.RUNNING, switchedPlayer.status)
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
        assertNull(closed.turnState)
        assertEquals(baseState.players, ticked.players)
        assertEquals(baseState.turnOrder, timedOut.turnOrder)
        assertEquals(1, ticked.stateVersion)
        assertEquals(1, timedOut.stateVersion)
        assertEquals(1, ticked.processedEventCount)
        assertEquals(1, timedOut.processedEventCount)
    }

    @Test
    fun `state version increases strictly with each reducer apply`() {
        val lobbyCode = LobbyCode("SV34")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val baseState =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne, playerTwo),
                turnOrder = listOf(playerOne, playerTwo),
                activePlayer = playerOne,
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                status = GameStatus.RUNNING,
            )

        val afterFirstAdvance = reducer.apply(baseState, TurnEnded(lobbyCode, playerOne))
        val afterSecondAdvance = reducer.apply(afterFirstAdvance, TurnEnded(lobbyCode, playerOne))

        assertEquals(0, baseState.stateVersion)
        assertEquals(1, afterFirstAdvance.stateVersion)
        assertEquals(2, afterSecondAdvance.stateVersion)
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
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, runningState.status)
        assertEquals(playerOne, runningState.activePlayer)
        assertEquals(TurnPhase.REINFORCEMENTS, runningState.turnState?.turnPhase)

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
        assertEquals(playerOne, removingWithoutActive.activePlayer)

        val removingActive = reducer.apply(baseState, PlayerLeft(lobbyCode, playerTwo))
        assertEquals(playerThree, removingActive.activePlayer)
        assertEquals(listOf(playerOne, playerThree), removingActive.turnOrder)
        assertEquals(TurnPhase.REINFORCEMENTS, removingActive.turnState?.turnPhase)

        val singlePlayerState =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne),
                turnOrder = listOf(playerOne),
                activePlayer = playerOne,
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
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
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                status = GameStatus.RUNNING,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                runningState.copy(
                    activePlayer = playerTwo,
                    turnState = runningState.turnState?.copy(activePlayerId = playerTwo),
                ),
                TurnEnded(lobbyCode, playerOne),
            )
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                runningState.copy(activePlayer = null, turnState = null),
                TurnEnded(lobbyCode, playerOne),
            )
        }
    }

    @Test
    fun `turn ended increments round count only when start player becomes active again`() {
        val lobbyCode = LobbyCode("OP58")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        var state =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne, playerTwo),
                turnOrder = listOf(playerOne, playerTwo),
                activePlayer = playerOne,
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                status = GameStatus.RUNNING,
            )

        repeat(4) {
            state = reducer.apply(state, TurnEnded(lobbyCode, playerOne))
        }
        assertEquals(playerTwo, state.activePlayer)
        assertEquals(1, state.turnState?.turnCount)

        repeat(4) {
            state = reducer.apply(state, TurnEnded(lobbyCode, playerTwo))
        }
        assertEquals(playerOne, state.activePlayer)
        assertEquals(2, state.turnState?.turnCount)
        assertEquals(2, state.turnNumber)
    }

    @Test
    fun `turn state updated event applies all fields atomically`() {
        val lobbyCode = LobbyCode("TS62")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val baseState =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne, playerTwo),
                turnOrder = listOf(playerOne, playerTwo),
                activePlayer = playerOne,
                turnNumber = 1,
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                status = GameStatus.RUNNING,
            )

        val updated =
            reducer.apply(
                baseState,
                TurnStateUpdatedEvent(
                    lobbyCode = lobbyCode,
                    activePlayerId = playerTwo,
                    turnPhase = TurnPhase.FORTIFY,
                    turnCount = 2,
                    startPlayerId = playerOne,
                    isPaused = true,
                    pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                    pausedPlayerId = playerTwo,
                ),
            )

        assertEquals(playerTwo, updated.activePlayer)
        assertEquals(2, updated.turnNumber)
        assertEquals(playerTwo, updated.turnState?.activePlayerId)
        assertEquals(TurnPhase.FORTIFY, updated.turnState?.turnPhase)
        assertEquals(2, updated.turnState?.turnCount)
        assertEquals(playerOne, updated.turnState?.startPlayerId)
        assertEquals(true, updated.turnState?.isPaused)
        assertEquals(TurnPauseReasons.WAITING_FOR_PLAYER, updated.turnState?.pauseReason)
        assertEquals(playerTwo, updated.turnState?.pausedPlayerId)
    }

    @Test
    fun `turn state updated event rejects invalid player and decreasing turn count`() {
        val lobbyCode = LobbyCode("TS64")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val baseState =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne, playerTwo),
                turnOrder = listOf(playerOne, playerTwo),
                activePlayer = playerOne,
                turnNumber = 3,
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
                        turnCount = 3,
                        startPlayerId = playerOne,
                    ),
                status = GameStatus.RUNNING,
            )

        val unknownPlayerException =
            assertThrows(InvalidLobbyEventException::class.java) {
                reducer.apply(
                    baseState,
                    TurnStateUpdatedEvent(
                        lobbyCode = lobbyCode,
                        activePlayerId = PlayerId(99),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 4,
                        startPlayerId = playerOne,
                    ),
                )
            }
        assertEquals(
            "TurnStateUpdatedEvent.activePlayerId '99' ist nicht Teil der Lobby '$lobbyCode'.",
            unknownPlayerException.message,
        )

        val backwardsException =
            assertThrows(InvalidLobbyEventException::class.java) {
                reducer.apply(
                    baseState,
                    TurnStateUpdatedEvent(
                        lobbyCode = lobbyCode,
                        activePlayerId = playerTwo,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 2,
                        startPlayerId = playerOne,
                    ),
                )
            }
        assertEquals(
            "TurnStateUpdatedEvent.turnCount darf nicht rückwärts laufen: aktuell=3, neu=2.",
            backwardsException.message,
        )

        val pausedMismatchException =
            assertThrows(IllegalArgumentException::class.java) {
                TurnStateUpdatedEvent(
                    lobbyCode = lobbyCode,
                    activePlayerId = playerTwo,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    turnCount = 4,
                    startPlayerId = playerOne,
                    isPaused = true,
                    pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                    pausedPlayerId = playerOne,
                )
            }
        assertEquals(
            "TurnStateUpdatedEvent.pausedPlayerId muss dem aktiven Spieler entsprechen.",
            pausedMismatchException.message,
        )
    }

    @Test
    fun `turn state respects stable player order during wrap around`() {
        val lobbyCode = LobbyCode("OP60")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val playerThree = PlayerId(3)
        var state =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne, playerTwo, playerThree),
                turnOrder = listOf(playerOne, playerTwo, playerThree),
                activePlayer = playerThree,
                turnState =
                    TurnState(
                        activePlayerId = playerThree,
                        turnPhase = TurnPhase.DRAW_CARD,
                        turnCount = 3,
                        startPlayerId = playerOne,
                    ),
                status = GameStatus.RUNNING,
            )

        state = reducer.apply(state, TurnEnded(lobbyCode, playerThree))

        assertEquals(playerOne, state.activePlayer)
        assertEquals(TurnPhase.REINFORCEMENTS, state.turnState?.turnPhase)
        assertEquals(4, state.turnState?.turnCount)
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
        assertEquals(TurnPhase.REINFORCEMENTS, updated.turnState?.turnPhase)
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
    fun `game started transitions status to running and initializes first turn`() {
        val lobbyCode = LobbyCode("GS01")
        val owner = PlayerId(1)
        val player2 = PlayerId(2)
        val player3 = PlayerId(3)
        val seed = 123L
        val random = Random(seed)
        val expectedTurnOrder = listOf(owner, player2, player3).shuffled(random)
        val expectedTerritoryOwners =
            sampleMapDefinition()
                .territories
                .map { territory -> territory.territoryId }
                .shuffled(random)
                .mapIndexed { index, territoryId ->
                    territoryId to expectedTurnOrder[index % expectedTurnOrder.size]
                }.toMap()
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, player2, player3),
                turnOrder = listOf(owner, player2, player3),
                activePlayer = owner,
                mapDefinition = sampleMapDefinition(),
                territoryStates =
                    sampleMapDefinition().territories.associate { territory ->
                        territory.territoryId to TerritoryState(territory.territoryId)
                    },
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

        val started = reducer.apply(stateWithOwner, GameStarted(lobbyCode, randomSeed = seed))

        assertEquals(GameStatus.RUNNING, started.status)
        assertEquals(true, started.gameStarted)
        assertEquals(expectedTurnOrder, started.turnOrder)
        assertEquals(expectedTurnOrder.first(), started.activePlayer)
        assertEquals(expectedTurnOrder.first(), started.turnState?.activePlayerId)
        assertEquals(TurnPhase.REINFORCEMENTS, started.turnState?.turnPhase)
        assertEquals(1, started.turnState?.turnCount)
        assertEquals(expectedTurnOrder.first(), started.turnState?.startPlayerId)
        assertEquals(false, started.turnState?.isPaused)
        assertEquals(null, started.turnState?.pauseReason)
        assertEquals(34, started.setupTroopsToPlaceFor(owner))
        assertEquals(34, started.setupTroopsToPlaceFor(player2))
        assertEquals(34, started.setupTroopsToPlaceFor(player3))
        started.allTerritoryStates().forEach { territoryState ->
            assertEquals(expectedTerritoryOwners[territoryState.territoryId], territoryState.ownerId)
            assertEquals(1, territoryState.troopCount)
        }
    }

    @Test
    fun `game started requires at least 3 players`() {
        val lobbyCode = LobbyCode("GS02")
        val owner = PlayerId(1)
        val player2 = PlayerId(2)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, player2),
                turnOrder = listOf(owner, player2),
                activePlayer = owner,
                mapDefinition = sampleMapDefinition(),
                territoryStates =
                    sampleMapDefinition().territories.associate { territory ->
                        territory.territoryId to TerritoryState(territory.territoryId)
                    },
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(stateWithOwner, GameStarted(lobbyCode))
        }
    }

    @Test
    fun `game started requires not already started`() {
        val lobbyCode = LobbyCode("GS03")
        val owner = PlayerId(1)
        val player2 = PlayerId(2)
        val player3 = PlayerId(3)
        val stateAlreadyRunning =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, player2, player3),
                configuredStartPlayerId = owner,
                turnOrder = listOf(owner, player2, player3),
                activePlayer = owner,
                mapDefinition = sampleMapDefinition(),
                territoryStates =
                    sampleMapDefinition().territories.associate { territory ->
                        territory.territoryId to TerritoryState(territory.territoryId)
                    },
                gameStarted = true,
                status = GameStatus.RUNNING,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(stateAlreadyRunning, GameStarted(lobbyCode))
        }
    }

    @Test
    fun `start player configured updates setup turn state before game start`() {
        val lobbyCode = LobbyCode("SP01")
        val owner = PlayerId(1)
        val player2 = PlayerId(2)
        val preGameState =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, player2),
                configuredStartPlayerId = owner,
                turnOrder = listOf(owner, player2),
                activePlayer = owner,
                turnState =
                    TurnState(
                        activePlayerId = owner,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = owner,
                    ),
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

        val updated =
            reducer.apply(
                preGameState,
                StartPlayerConfigured(
                    lobbyCode = lobbyCode,
                    startPlayerId = player2,
                    requesterPlayerId = owner,
                ),
            )

        assertEquals(player2, updated.configuredStartPlayerId)
        assertEquals(player2, updated.activePlayer)
        assertEquals(player2, updated.turnState?.activePlayerId)
        assertEquals(player2, updated.turnState?.startPlayerId)
        assertEquals(TurnPhase.REINFORCEMENTS, updated.turnState?.turnPhase)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, updated.status)
    }

    @Test
    fun `start player configured rejects non member and after started`() {
        val lobbyCode = LobbyCode("SP02")
        val owner = PlayerId(1)
        val player2 = PlayerId(2)
        val preGameState =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, player2),
                configuredStartPlayerId = owner,
                turnOrder = listOf(owner, player2),
                activePlayer = owner,
                turnState =
                    TurnState(
                        activePlayerId = owner,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = owner,
                    ),
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                preGameState,
                StartPlayerConfigured(
                    lobbyCode = lobbyCode,
                    startPlayerId = PlayerId(99),
                    requesterPlayerId = owner,
                ),
            )
        }

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                preGameState.copy(gameStarted = true, status = GameStatus.RUNNING),
                StartPlayerConfigured(
                    lobbyCode = lobbyCode,
                    startPlayerId = player2,
                    requesterPlayerId = owner,
                ),
            )
        }
    }

    @Test
    fun `game started uses configured start player as initial active player`() {
        val lobbyCode = LobbyCode("SP03")
        val owner = PlayerId(1)
        val player2 = PlayerId(2)
        val player3 = PlayerId(3)
        val preGameState =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, player2, player3),
                configuredStartPlayerId = player2,
                turnOrder = listOf(owner, player2, player3),
                activePlayer = player2,
                mapDefinition = sampleMapDefinition(),
                territoryStates =
                    sampleMapDefinition().territories.associate { territory ->
                        territory.territoryId to TerritoryState(territory.territoryId)
                    },
                turnState =
                    TurnState(
                        activePlayerId = player2,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = player2,
                    ),
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

        val started = reducer.apply(preGameState, GameStarted(lobbyCode))

        assertEquals(GameStatus.RUNNING, started.status)
        assertEquals(true, started.gameStarted)
        assertEquals(player2, started.activePlayer)
        assertEquals(player2, started.turnState?.activePlayerId)
        assertEquals(player2, started.turnState?.startPlayerId)
    }

    @Test
    fun `territory owner changed aktualisiert owner`() {
        val lobbyCode = LobbyCode("TM10")
        val playerOne = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            )

        val updated =
            reducer.apply(
                initialState,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), playerOne),
            )

        assertEquals(playerOne, updated.territoryOwnerOf(TerritoryId("alpha")))
    }

    @Test
    fun `territory troops changed aktualisiert troop count`() {
        val lobbyCode = LobbyCode("TM12")
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
            )

        val updated =
            reducer.apply(
                initialState,
                TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("alpha"), 7),
            )

        assertEquals(7, updated.troopCountOf(TerritoryId("alpha")))
    }

    @Test
    fun `territory event mit unknown territory fuehrt zu fail`() {
        val lobbyCode = LobbyCode("TM14")
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(PlayerId(1)),
            )

        val exception =
            assertThrows(InvalidLobbyEventException::class.java) {
                reducer.apply(
                    initialState,
                    TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("missing"), PlayerId(1)),
                )
            }

        assertEquals(
            "Territory 'missing' ist nicht Teil der Map von Lobby '$lobbyCode'.",
            exception.message,
        )
    }

    @Test
    fun `bonus query reagiert korrekt auf event sequenz`() {
        val lobbyCode = LobbyCode("TM16")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne, playerTwo),
            )

        val afterAlpha =
            reducer.apply(
                initialState,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), playerOne),
            )
        val afterBeta =
            reducer.apply(
                afterAlpha,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("beta"), playerOne),
            )
        val afterGamma =
            reducer.apply(
                afterBeta,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("gamma"), playerTwo),
            )

        assertEquals(playerOne, afterBeta.continentOwner(ContinentId("north")))
        assertEquals(3, afterBeta.bonusFor(playerOne))
        assertNull(afterBeta.continentOwner(ContinentId("south")))
        assertEquals(playerTwo, afterGamma.continentOwner(ContinentId("south")))
        assertEquals(1, afterGamma.bonusFor(playerTwo))
    }

    private fun sampleMapDefinition(): MapDefinition =
        MapDefinition(
            schemaVersion = 1,
            territories =
                listOf(
                    TerritoryDefinition(
                        territoryId = TerritoryId("alpha"),
                        edges =
                            listOf(
                                TerritoryEdgeDefinition(TerritoryId("beta")),
                                TerritoryEdgeDefinition(TerritoryId("gamma")),
                            ),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("beta"),
                        edges = listOf(TerritoryEdgeDefinition(TerritoryId("alpha"))),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("gamma"),
                        edges = listOf(TerritoryEdgeDefinition(TerritoryId("alpha"))),
                    ),
                ),
            continents =
                listOf(
                    ContinentDefinition(
                        continentId = ContinentId("north"),
                        territoryIds = listOf(TerritoryId("alpha"), TerritoryId("beta")),
                        bonusValue = 3,
                    ),
                    ContinentDefinition(
                        continentId = ContinentId("south"),
                        territoryIds = listOf(TerritoryId("gamma")),
                        bonusValue = 1,
                    ),
                ),
        )
}
