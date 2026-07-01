package at.aau.pulverfass.shared.lobby.reducer

import at.aau.pulverfass.shared.event.CorrelationId
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardDrawnEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.CheatReinforcementBonusUsedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.MatchEndReason
import at.aau.pulverfass.shared.lobby.event.MatchEndedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsSetEvent
import at.aau.pulverfass.shared.lobby.event.PlayerCardsRemovedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
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
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.DeckState
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.HandState
import at.aau.pulverfass.shared.lobby.state.PendingReinforcements
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Tests für den Domain-Reducer.
 *
 * Der Reducer ist die Stelle, an der aus einem gültigen LobbyEvent ein neuer
 * GameState entsteht. Für die Prüfung ist die Grundidee wichtig: Regeln wie
 * "darf der Spieler den Request senden?" liegen im Routing, aber ein bereits
 * akzeptiertes Event muss hier deterministisch und reproduzierbar angewendet
 * werden.
 */
class DefaultLobbyEventReducerTest {
    private val reducer = DefaultLobbyEventReducer()

    @Test
    fun `player joined verändert state korrekt`() {
        val lobbyCode = LobbyCode("1003")
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
        assertEquals("ALICE", updatedState.playerDisplayNames.getValue(playerId))
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
        val lobbyCode = LobbyCode("1071")
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
        val lobbyCode = LobbyCode("1132")
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
        val lobbyCode = LobbyCode("1178")
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
        val expectedLobbyCode = LobbyCode("1003")
        val actualLobbyCode = LobbyCode("1071")
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
        val lobbyCode = LobbyCode("1199")
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
        val lobbyCode = LobbyCode("1360")
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
        val lobbyCode = LobbyCode("1205")
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
            reducer.apply(duplicateState, PlayerJoined(lobbyCode, playerOne, "ALICE"))
        }

        val runningState =
            reducer.apply(
                duplicateState,
                PlayerJoined(lobbyCode, playerTwo, "BOB"),
            )
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, runningState.status)
        assertEquals(playerOne, runningState.activePlayer)
        assertEquals(TurnPhase.REINFORCEMENTS, runningState.turnState?.turnPhase)

        val startedState =
            duplicateState.copy(
                gameStarted = true,
                status = GameStatus.RUNNING,
            )
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(startedState, PlayerJoined(lobbyCode, playerTwo, "BOB"))
        }

        val closedState = duplicateState.copy(status = GameStatus.CLOSED)
        val finishedState = duplicateState.copy(status = GameStatus.FINISHED)

        assertEquals(
            GameStatus.CLOSED,
            reducer.apply(closedState, PlayerJoined(lobbyCode, playerTwo, "BOB")).status,
        )
        assertEquals(
            GameStatus.FINISHED,
            reducer.apply(finishedState, PlayerJoined(lobbyCode, playerTwo, "BOB")).status,
        )
    }

    @Test
    fun `player left handles active player and status transitions`() {
        val lobbyCode = LobbyCode("1247")
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

        val twoPlayerRunningState =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne, playerTwo),
                playerDisplayNames =
                    mapOf(
                        playerOne to "1",
                        playerTwo to "2",
                    ),
                turnOrder = listOf(playerOne, playerTwo),
                activePlayer = playerTwo,
                turnState =
                    TurnState(
                        activePlayerId = playerTwo,
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                gameStarted = true,
                status = GameStatus.RUNNING,
                setupTroopsToPlaceByPlayer =
                    mapOf(
                        playerOne to 0,
                        playerTwo to 0,
                    ),
            )
        val finishedByLastRemainingPlayer =
            reducer.apply(twoPlayerRunningState, PlayerLeft(lobbyCode, playerTwo))
        assertEquals(GameStatus.FINISHED, finishedByLastRemainingPlayer.status)

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
    fun `player left clears owned territories and hand cards`() {
        val lobbyCode = LobbyCode("1248")
        val owner = PlayerId(1)
        val leavingPlayer = PlayerId(2)
        val remainingPlayer = PlayerId(3)
        val mapDefinition = sampleMapDefinition()
        val ownerCard = CardState(CardId("owner-card"), CardType.A)
        val leavingCard = CardState(CardId("leaving-card"), CardType.B)
        val state =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(owner, leavingPlayer, remainingPlayer),
                playerDisplayNames =
                    mapOf(
                        owner to "OWNER",
                        leavingPlayer to "LEFT",
                        remainingPlayer to "THIRD",
                    ),
                lobbyOwner = owner,
                activePlayer = owner,
                turnOrder = listOf(owner, leavingPlayer, remainingPlayer),
                turnState =
                    TurnState(
                        activePlayerId = owner,
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 2,
                        startPlayerId = owner,
                    ),
                gameStarted = true,
                status = GameStatus.RUNNING,
                mapDefinition = mapDefinition,
                territoryStates =
                    mapDefinition.territories.associate { territory ->
                        territory.territoryId to
                            when (territory.territoryId.value) {
                                "alpha" -> TerritoryState(territory.territoryId, leavingPlayer, 4)
                                "beta" -> TerritoryState(territory.territoryId, owner, 3)
                                else -> TerritoryState(territory.territoryId, remainingPlayer, 2)
                            }
                    },
                handState =
                    HandState(
                        cardsByPlayer =
                            mapOf(
                                owner to listOf(ownerCard),
                                leavingPlayer to listOf(leavingCard),
                            ),
                    ),
            )

        val updated = reducer.apply(state, PlayerLeft(lobbyCode, leavingPlayer))

        assertNull(updated.territoryOwnerOf(TerritoryId("alpha")))
        assertEquals(0, updated.requireTerritoryState(TerritoryId("alpha")).troopCount)
        assertEquals(owner, updated.territoryOwnerOf(TerritoryId("beta")))
        assertEquals(listOf(ownerCard), updated.handOf(owner))
        assertEquals(emptyList<CardState>(), updated.handOf(leavingPlayer))
        assertEquals(false, updated.handState.cardsByPlayer.containsKey(leavingPlayer))
    }

    @Test
    fun `player eliminated removes player from turn order but keeps spectator in lobby`() {
        val lobbyCode = LobbyCode("1135")
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(attacker, defender),
            ).copy(
                activePlayer = attacker,
                turnOrder = listOf(attacker, defender),
                turnState =
                    TurnState(
                        activePlayerId = attacker,
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 1,
                        startPlayerId = attacker,
                    ),
                gameStarted = true,
                status = GameStatus.RUNNING,
                territoryStates =
                    GameState.initial(
                        lobbyCode = lobbyCode,
                        mapDefinition = sampleMapDefinition(),
                        players = listOf(attacker, defender),
                    ).allTerritoryStates().associate { territoryState ->
                        val territoryId = territoryState.territoryId
                        territoryId to
                            when (territoryId.value) {
                                "alpha" -> TerritoryState(territoryId, attacker, 2)
                                "beta" -> TerritoryState(territoryId, attacker, 3)
                                else -> TerritoryState(territoryId, attacker, 1)
                            }
                    },
            )

        val updated =
            reducer.apply(
                baseState,
                PlayerEliminatedEvent(
                    lobbyCode = lobbyCode,
                    playerId = defender,
                    eliminatedByPlayerId = attacker,
                ),
            )

        assertEquals(listOf(attacker, defender), updated.players)
        assertEquals(listOf(attacker), updated.turnOrder)
        assertEquals(true, updated.isSpectator(defender))
        assertEquals(GameStatus.FINISHED, updated.status)
    }

    @Test
    fun `player eliminated rejects invalid lifecycle and territory preconditions`() {
        val lobbyCode = LobbyCode("1137")
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(attacker, defender),
            ).copy(
                activePlayer = attacker,
                turnOrder = listOf(attacker, defender),
                turnState =
                    TurnState(
                        activePlayerId = attacker,
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 1,
                        startPlayerId = attacker,
                    ),
                status = GameStatus.RUNNING,
            )

        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(
                baseState.copy(gameStarted = false, status = GameStatus.WAITING_FOR_PLAYERS),
                PlayerEliminatedEvent(lobbyCode, defender, attacker),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(
                baseState.copy(turnOrder = listOf(attacker)),
                PlayerEliminatedEvent(lobbyCode, defender, attacker),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(
                baseState.copy(
                    gameStarted = true,
                    territoryStates =
                        baseState.allTerritoryStates().associate { territoryState ->
                            val territoryId = territoryState.territoryId
                            territoryId to
                                when (territoryId.value) {
                                    "alpha" -> TerritoryState(territoryId, defender, 1)
                                    else -> TerritoryState(territoryId, attacker, 1)
                                }
                        },
                ),
                PlayerEliminatedEvent(lobbyCode, defender, attacker),
            )
        }
    }

    @Test
    fun `player eliminated clears turn state when last active player is removed`() {
        val lobbyCode = LobbyCode("1138")
        val eliminatedPlayer = PlayerId(1)
        val attacker = PlayerId(2)
        val state =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(eliminatedPlayer, attacker),
            ).copy(
                activePlayer = eliminatedPlayer,
                turnOrder = listOf(eliminatedPlayer),
                turnNumber = 4,
                turnState =
                    TurnState(
                        activePlayerId = eliminatedPlayer,
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 4,
                        startPlayerId = eliminatedPlayer,
                    ),
                gameStarted = true,
                status = GameStatus.RUNNING,
                territoryStates =
                    GameState.initial(
                        lobbyCode = lobbyCode,
                        mapDefinition = sampleMapDefinition(),
                        players = listOf(eliminatedPlayer, attacker),
                    ).allTerritoryStates().associate { territoryState ->
                        territoryState.territoryId to
                            TerritoryState(territoryState.territoryId, null, 0)
                    },
            )

        val updated =
            reducer.apply(
                state,
                PlayerEliminatedEvent(lobbyCode, eliminatedPlayer, attacker),
            )

        assertNull(updated.activePlayer)
        assertEquals(0, updated.turnNumber)
        assertNull(updated.turnState)
    }

    @Test
    fun `attack resolved rejects invalid reducer preconditions`() {
        val lobbyCode = LobbyCode("1010")
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val baseState = runningAttackState(lobbyCode, attacker, defender)
        val validEvent = validAttackResolvedEvent(lobbyCode, attacker, defender)

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(baseState.copy(gameRandomSeed = null, gameRandomState = null), validEvent)
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(baseState.copy(gameRandomState = null), validEvent)
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(baseState, validEvent.copy(rngStateBefore = 99L))
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                baseState.copy(
                    territoryStates =
                        baseState.territoryStates +
                            (
                                TerritoryId("alpha") to
                                    TerritoryState(TerritoryId("alpha"), defender, 5)
                            ),
                ),
                validEvent,
            )
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                baseState.copy(
                    territoryStates =
                        baseState.territoryStates +
                            (
                                TerritoryId("beta") to
                                    TerritoryState(TerritoryId("beta"), attacker, 2)
                            ),
                ),
                validEvent,
            )
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                baseState.copy(
                    territoryStates =
                        baseState.territoryStates +
                            (
                                TerritoryId("alpha") to
                                    TerritoryState(TerritoryId("alpha"), attacker, 4)
                            ),
                ),
                validEvent,
            )
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                baseState.copy(
                    territoryStates =
                        baseState.territoryStates +
                            (
                                TerritoryId("beta") to
                                    TerritoryState(TerritoryId("beta"), defender, 3)
                            ),
                ),
                validEvent,
            )
        }
    }

    @Test
    fun `attack resolved rejects invalid capture occupation payload in reducer`() {
        val lobbyCode = LobbyCode("1011")
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val baseState = runningAttackState(lobbyCode, attacker, defender)
        val captureEvent =
            validAttackResolvedEvent(
                lobbyCode = lobbyCode,
                attacker = attacker,
                defender = defender,
                attackerLosses = 0,
                defenderLosses = 2,
                attackerRemaining = 5,
                defenderRemaining = 0,
                occupyingTroopCount = 3,
                minOccupyingTroops = 3,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(baseState, captureEvent.copy(occupyingTroopCount = 2))
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                baseState,
                AttackResolvedEvent(
                    lobbyCode = lobbyCode,
                    attackerPlayerId = attacker,
                    defenderPlayerId = defender,
                    fromTerritoryId = TerritoryId("alpha"),
                    toTerritoryId = TerritoryId("beta"),
                    attackTroops = 3,
                    sourceTroopsBefore = 4,
                    targetTroopsBefore = 1,
                    requestedAttackDice = 3,
                    attackDice = 2,
                    defendDice = 2,
                    attackerRolls = listOf(6, 5),
                    defenderRolls = listOf(2, 1),
                    rngTrace = listOf(6, 5, 2, 1),
                    rngStateBefore = 2L,
                    rngStateAfter = 3L,
                    attackerLosses = 1,
                    defenderLosses = 1,
                    attackerRemaining = 3,
                    defenderRemaining = 0,
                    occupyingTroopCount = 3,
                    minOccupyingTroops = 1,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(
                baseState,
                captureEvent.copy(defenderRemaining = 1, defenderLosses = 1),
            )
        }
    }

    @Test
    fun `capture marks turn eligible for one drawn card`() {
        val lobbyCode = LobbyCode("1012")
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val captureEvent =
            validAttackResolvedEvent(
                lobbyCode = lobbyCode,
                attacker = attacker,
                defender = defender,
                attackerLosses = 0,
                defenderLosses = 2,
                attackerRemaining = 5,
                defenderRemaining = 0,
                occupyingTroopCount = 3,
                minOccupyingTroops = 3,
            )

        val afterCapture =
            reducer.apply(runningAttackState(lobbyCode, attacker, defender), captureEvent)

        assertEquals(true, afterCapture.territoryCapturedThisTurn)
        assertEquals(attacker, afterCapture.ownerOf(TerritoryId("beta")))
    }

    @Test
    fun `attack without capture does not mark turn eligible for drawn card`() {
        val lobbyCode = LobbyCode("1013")
        val attacker = PlayerId(1)
        val defender = PlayerId(2)

        val updatedState =
            reducer.apply(
                runningAttackState(lobbyCode, attacker, defender),
                validAttackResolvedEvent(lobbyCode, attacker, defender),
            )

        assertEquals(false, updatedState.territoryCapturedThisTurn)
    }

    @Test
    fun `card drawn event moves exactly one deck card to player hand`() {
        val lobbyCode = LobbyCode("1068")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val firstCard = CardState(CardId("deck-1"), CardType.A)
        val secondCard = CardState(CardId("deck-2"), CardType.B)
        val drawReadyState =
            runningAttackState(lobbyCode, playerOne, playerTwo).copy(
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                deckState = DeckState(listOf(firstCard, secondCard)),
                territoryCapturedThisTurn = true,
            )

        val updatedState =
            reducer.apply(
                drawReadyState,
                CardDrawnEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerOne,
                    cardId = firstCard.cardId,
                ),
            )

        assertEquals(listOf(firstCard), updatedState.handOf(playerOne))
        assertEquals(listOf(secondCard), updatedState.deckState.cards)
        assertEquals(false, updatedState.territoryCapturedThisTurn)
    }

    @Test
    fun `card drawn event rejects missing capture or wrong phase`() {
        val lobbyCode = LobbyCode("1069")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val firstCard = CardState(CardId("deck-1"), CardType.A)
        val drawReadyState =
            runningAttackState(lobbyCode, playerOne, playerTwo).copy(
                deckState = DeckState(listOf(firstCard)),
                territoryCapturedThisTurn = true,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                drawReadyState,
                CardDrawnEvent(lobbyCode, playerOne, firstCard.cardId),
            )
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                drawReadyState.copy(
                    turnState =
                        TurnState(
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.DRAW_CARD,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                    territoryCapturedThisTurn = false,
                ),
                CardDrawnEvent(lobbyCode, playerOne, firstCard.cardId),
            )
        }
    }

    @Test
    fun `card drawn event rejects missing turn state or inactive player`() {
        val lobbyCode = LobbyCode("1070")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val firstCard = CardState(CardId("deck-1"), CardType.A)
        val drawReadyState =
            runningAttackState(lobbyCode, playerOne, playerTwo).copy(
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                deckState = DeckState(listOf(firstCard)),
                territoryCapturedThisTurn = true,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                drawReadyState.copy(activePlayer = null, turnState = null),
                CardDrawnEvent(lobbyCode, playerOne, firstCard.cardId),
            )
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                drawReadyState,
                CardDrawnEvent(lobbyCode, playerTwo, firstCard.cardId),
            )
        }
    }

    @Test
    fun `match ended event finishes running game with reason`() {
        val lobbyCode = LobbyCode("1243")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val runningState =
            runningAttackState(lobbyCode, playerOne, playerTwo).copy(
                pendingReinforcements = PendingReinforcements(playerOne, 3),
                territoryCapturedThisTurn = true,
            )

        val updatedState =
            reducer.apply(
                runningState,
                MatchEndedEvent(lobbyCode, MatchEndReason.DECK_EMPTY),
            )

        assertEquals(GameStatus.FINISHED, updatedState.status)
        assertEquals(MatchEndReason.DECK_EMPTY.name, updatedState.closedReason)
        assertNull(updatedState.winnerPlayerId)
        assertEquals(null, updatedState.pendingReinforcements)
        assertEquals(false, updatedState.territoryCapturedThisTurn)
    }

    @Test
    fun `match ended event stores territory domination winner`() {
        val lobbyCode = LobbyCode("1245")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val runningState = runningAttackState(lobbyCode, playerOne, playerTwo)

        val updatedState =
            reducer.apply(
                runningState,
                MatchEndedEvent(
                    lobbyCode = lobbyCode,
                    reason = MatchEndReason.TERRITORY_DOMINATION,
                    winnerPlayerId = playerOne,
                ),
            )

        assertEquals(GameStatus.FINISHED, updatedState.status)
        assertEquals(MatchEndReason.TERRITORY_DOMINATION.name, updatedState.closedReason)
        assertEquals(playerOne, updatedState.winnerPlayerId)
    }

    @Test
    fun `match ended event rejects non running game`() {
        val lobbyCode = LobbyCode("1244")
        val state = GameState.initial(lobbyCode)

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                state,
                MatchEndedEvent(lobbyCode, MatchEndReason.DECK_EMPTY),
            )
        }
    }

    @Test
    fun `territory owner changed finishes match when one player controls all territories`() {
        val lobbyCode = LobbyCode("1246")
        val winner = PlayerId(1)
        val defender = PlayerId(2)
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(winner, defender),
            ).copy(
                gameStarted = true,
                status = GameStatus.RUNNING,
                territoryStates =
                    GameState.initial(
                        lobbyCode = lobbyCode,
                        mapDefinition = sampleMapDefinition(),
                        players = listOf(winner, defender),
                    ).allTerritoryStates().associate { territoryState ->
                        val ownerId =
                            if (territoryState.territoryId == TerritoryId("alpha")) {
                                defender
                            } else {
                                winner
                            }
                        territoryState.territoryId to
                            TerritoryState(
                                territoryId = territoryState.territoryId,
                                ownerId = ownerId,
                                troopCount = 1,
                            )
                    },
            )

        val updatedState =
            reducer.apply(
                baseState,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), winner),
            )

        assertEquals(GameStatus.FINISHED, updatedState.status)
        assertEquals(MatchEndReason.TERRITORY_DOMINATION.name, updatedState.closedReason)
        assertEquals(winner, updatedState.winnerPlayerId)
    }

    @Test
    fun `player eliminated transfers defender cards and sets next reinforcement trade flag`() {
        val lobbyCode = LobbyCode("1136")
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val attackerCardA = CardState(CardId("a-1"), CardType.A)
        val attackerCardB = CardState(CardId("b-1"), CardType.B)
        val defenderCardC = CardState(CardId("c-1"), CardType.C)
        val defenderCardJoker = CardState(CardId("j-1"), CardType.JOKER)
        val defenderCardA = CardState(CardId("a-2"), CardType.A)
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(attacker, defender),
            ).copy(
                activePlayer = attacker,
                turnOrder = listOf(attacker, defender),
                turnState =
                    TurnState(
                        activePlayerId = attacker,
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 1,
                        startPlayerId = attacker,
                    ),
                gameStarted = true,
                status = GameStatus.RUNNING,
                territoryStates =
                    GameState.initial(
                        lobbyCode = lobbyCode,
                        mapDefinition = sampleMapDefinition(),
                        players = listOf(attacker, defender),
                    ).allTerritoryStates().associate { territoryState ->
                        territoryState.territoryId to
                            TerritoryState(
                                territoryId = territoryState.territoryId,
                                ownerId = attacker,
                                troopCount = 2,
                            )
                    },
            )
                .withCardAddedToHand(attacker, attackerCardA)
                .withCardAddedToHand(attacker, attackerCardB)
                .withCardAddedToHand(defender, defenderCardC)
                .withCardAddedToHand(defender, defenderCardJoker)
                .withCardAddedToHand(defender, defenderCardA)

        val updated =
            reducer.apply(
                baseState,
                PlayerEliminatedEvent(
                    lobbyCode = lobbyCode,
                    playerId = defender,
                    eliminatedByPlayerId = attacker,
                ),
            )

        assertEquals(
            listOf(
                attackerCardA,
                attackerCardB,
                defenderCardC,
                defenderCardJoker,
                defenderCardA,
            ),
            updated.handOf(attacker),
        )
        assertEquals(emptyList<CardState>(), updated.handOf(defender))
        assertEquals(true, updated.tradeRequiredOnNextReinforcementPhaseFor(attacker))
        assertEquals(false, updated.tradeRequiredOnNextReinforcementPhaseFor(defender))
    }

    @Test
    fun `turn ended validates active player`() {
        val lobbyCode = LobbyCode("1262")
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
        val lobbyCode = LobbyCode("1263")
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
        val lobbyCode = LobbyCode("1412")
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
        assertEquals(false, updated.fortifyUsedThisTurn)
    }

    @Test
    fun `fortify events update troops and turn flag through reducer only`() {
        val lobbyCode = LobbyCode("1159")
        val playerOne = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne, PlayerId(2)),
            ).copy(
                activePlayer = playerOne,
                turnOrder = listOf(playerOne, PlayerId(2)),
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.FORTIFY,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                status = GameStatus.RUNNING,
                territoryStates =
                    mapOf(
                        TerritoryId("alpha") to TerritoryState(TerritoryId("alpha"), playerOne, 5),
                        TerritoryId("beta") to TerritoryState(TerritoryId("beta"), playerOne, 1),
                        TerritoryId("gamma") to TerritoryState(TerritoryId("gamma"), playerOne, 2),
                    ),
            )

        val moved =
            reducer.apply(
                initialState,
                FortifyMoveAppliedEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerOne,
                    fromTerritoryId = TerritoryId("alpha"),
                    toTerritoryId = TerritoryId("gamma"),
                    troopCount = 3,
                ),
            )
        val marked =
            reducer.apply(
                moved,
                FortifyUsedSetEvent(
                    lobbyCode = lobbyCode,
                    used = true,
                ),
            )

        assertEquals(2, moved.troopCountOf(TerritoryId("alpha")))
        assertEquals(5, moved.troopCountOf(TerritoryId("gamma")))
        assertEquals(false, moved.fortifyUsedThisTurn)
        assertEquals(true, marked.fortifyUsedThisTurn)
    }

    @Test
    fun `turn change resets fortify used flag for next player turn`() {
        val lobbyCode = LobbyCode("1160")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val state =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne, playerTwo),
                turnOrder = listOf(playerOne, playerTwo),
                activePlayer = playerOne,
                turnNumber = 1,
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                fortifyUsedThisTurn = true,
                territoryCapturedThisTurn = true,
                status = GameStatus.RUNNING,
            )

        val updated =
            reducer.apply(
                state,
                TurnStateUpdatedEvent(
                    lobbyCode = lobbyCode,
                    activePlayerId = playerTwo,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    turnCount = 1,
                    startPlayerId = playerOne,
                ),
            )

        assertEquals(playerTwo, updated.activePlayer)
        assertEquals(false, updated.fortifyUsedThisTurn)
        assertEquals(false, updated.territoryCapturedThisTurn)
    }

    @Test
    fun `phase change preserves captured territory flag during same player turn`() {
        val lobbyCode = LobbyCode("1161")
        val playerOne = PlayerId(1)
        val state =
            GameState(
                lobbyCode = lobbyCode,
                players = listOf(playerOne),
                turnOrder = listOf(playerOne),
                activePlayer = playerOne,
                turnNumber = 1,
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.FORTIFY,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                territoryCapturedThisTurn = true,
                status = GameStatus.RUNNING,
            )

        val updated =
            reducer.apply(
                state,
                TurnStateUpdatedEvent(
                    lobbyCode = lobbyCode,
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.DRAW_CARD,
                    turnCount = 1,
                    startPlayerId = playerOne,
                ),
            )

        assertEquals(true, updated.territoryCapturedThisTurn)
    }

    @Test
    fun `turn state updated event rejects invalid player and decreasing turn count`() {
        val lobbyCode = LobbyCode("1413")
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
        val lobbyCode = LobbyCode("1264")
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
        val lobbyCode = LobbyCode("1313")
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
                PlayerJoined(lobbyCode, PlayerId(3), "CAROL"),
                null,
                4,
                null,
            ) as GameState

        assertEquals(PlayerId(3), updated.activePlayer)
        assertNull(updated.lastEventContext)
    }

    @Test
    fun `player kicked requires owner permission`() {
        val lobbyCode = LobbyCode("1355")
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
        val lobbyCode = LobbyCode("1424")
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
        val lobbyCode = LobbyCode("1426")
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
    fun `player kicked clears owned territories and hand cards`() {
        val lobbyCode = LobbyCode("1427")
        val owner = PlayerId(1)
        val targetPlayer = PlayerId(2)
        val thirdPlayer = PlayerId(3)
        val mapDefinition = sampleMapDefinition()
        val ownerCard = CardState(CardId("kick-owner-card"), CardType.A)
        val targetCard = CardState(CardId("kick-target-card"), CardType.C)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, targetPlayer, thirdPlayer),
                turnOrder = listOf(owner, targetPlayer, thirdPlayer),
                activePlayer = targetPlayer,
                turnState =
                    TurnState(
                        activePlayerId = targetPlayer,
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 3,
                        startPlayerId = owner,
                    ),
                gameStarted = true,
                status = GameStatus.RUNNING,
                mapDefinition = mapDefinition,
                territoryStates =
                    mapDefinition.territories.associate { territory ->
                        territory.territoryId to
                            when (territory.territoryId.value) {
                                "alpha" -> TerritoryState(territory.territoryId, targetPlayer, 5)
                                "beta" -> TerritoryState(territory.territoryId, owner, 4)
                                else -> TerritoryState(territory.territoryId, thirdPlayer, 2)
                            }
                    },
                handState =
                    HandState(
                        cardsByPlayer =
                            mapOf(
                                owner to listOf(ownerCard),
                                targetPlayer to listOf(targetCard),
                            ),
                    ),
            )

        val updated = reducer.apply(stateWithOwner, PlayerKicked(lobbyCode, targetPlayer, owner))

        assertNull(updated.territoryOwnerOf(TerritoryId("alpha")))
        assertEquals(0, updated.requireTerritoryState(TerritoryId("alpha")).troopCount)
        assertEquals(owner, updated.territoryOwnerOf(TerritoryId("beta")))
        assertEquals(listOf(ownerCard), updated.handOf(owner))
        assertEquals(emptyList<CardState>(), updated.handOf(targetPlayer))
        assertEquals(false, updated.handState.cardsByPlayer.containsKey(targetPlayer))
    }

    @Test
    fun `player kicked handles single player removal`() {
        val lobbyCode = LobbyCode("1436")
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
        assertEquals(GameStatus.FINISHED, updated.status)
    }

    @Test
    fun `player kicked transitions status when below 2 players`() {
        val lobbyCode = LobbyCode("1004")
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
        assertEquals(GameStatus.FINISHED, updated.status)
    }

    @Test
    fun `game started transitions status to running and initializes first turn`() {
        val lobbyCode = LobbyCode("1182")
        val owner = PlayerId(1)
        val player2 = PlayerId(2)
        val player3 = PlayerId(3)
        val seed = 123L
        val mapDefinition = defaultMapDefinition()
        val random = Random(seed)
        val expectedTurnOrder = listOf(owner, player2, player3).shuffled(random)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, player2, player3),
                turnOrder = listOf(owner, player2, player3),
                activePlayer = owner,
                mapDefinition = mapDefinition,
                territoryStates =
                    mapDefinition.territories.associate { territory ->
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
        val territoryCount = mapDefinition.territories.size
        assertEquals(territoryCount * 3 + 4, started.deckState.cards.size)
        assertEquals(
            territoryCount,
            started.deckState.cards.count { card -> card.type == CardType.A },
        )
        assertEquals(
            territoryCount,
            started.deckState.cards.count { card -> card.type == CardType.B },
        )
        assertEquals(
            territoryCount,
            started.deckState.cards.count { card -> card.type == CardType.C },
        )
        assertEquals(4, started.deckState.cards.count { card -> card.type == CardType.JOKER })
        assertEquals(
            started.deckState.cards.size,
            started.deckState.cards.map { card -> card.cardId }.distinct().size,
        )
        assertEquals(0, started.setupTroopsToPlaceFor(owner))
        assertEquals(0, started.setupTroopsToPlaceFor(player2))
        assertEquals(0, started.setupTroopsToPlaceFor(player3))
        assertEquals(
            60,
            started.allTerritoryStates().sumOf { territoryState -> territoryState.troopCount },
        )
        assertEquals(
            20,
            started.allTerritoryStates()
                .filter { territoryState -> territoryState.ownerId == owner }
                .sumOf { territoryState -> territoryState.troopCount },
        )
        assertEquals(
            20,
            started.allTerritoryStates()
                .filter { territoryState -> territoryState.ownerId == player2 }
                .sumOf { territoryState -> territoryState.troopCount },
        )
        assertEquals(
            20,
            started.allTerritoryStates()
                .filter { territoryState -> territoryState.ownerId == player3 }
                .sumOf { territoryState -> territoryState.troopCount },
        )
        assertEquals(
            true,
            started.allTerritoryStates().all { territoryState ->
                territoryState.ownerId != null && territoryState.troopCount in 1..4
            },
        )
        assertEquals(
            true,
            mapDefinition.continents
                .filter { continent -> continent.territoryIds.size >= 2 }
                .all { continent ->
                    continent.territoryIds
                        .map { territoryId -> started.requireTerritoryState(territoryId).ownerId }
                        .distinct()
                        .size >= 2
                },
        )
    }

    @Test
    fun `game started requires at least 3 players`() {
        val lobbyCode = LobbyCode("1183")
        val owner = PlayerId(1)
        val player2 = PlayerId(2)
        val stateWithOwner =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, player2),
                turnOrder = listOf(owner, player2),
                activePlayer = owner,
                mapDefinition = defaultMapDefinition(),
                territoryStates =
                    defaultMapDefinition().territories.associate { territory ->
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
        val lobbyCode = LobbyCode("1184")
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
        val lobbyCode = LobbyCode("1340")
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
        val lobbyCode = LobbyCode("1341")
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
        val lobbyCode = LobbyCode("1342")
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
                mapDefinition = defaultMapDefinition(),
                territoryStates =
                    defaultMapDefinition().territories.associate { territory ->
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
        val lobbyCode = LobbyCode("1392")
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
        val lobbyCode = LobbyCode("1393")
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
    fun `cheat reinforcement bonus used marks player`() {
        val lobbyCode = LobbyCode("1072")
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
                CheatReinforcementBonusUsedEvent(lobbyCode, playerOne),
            )

        /**
         * Der Reducer trägt den Spieler in das Set der bereits verwendeten
         * Cheatboni ein. Genau dieses Set verhindert später eine zweite Nutzung.
         */
        assertEquals(
            setOf(playerOne),
            updated.usedCheatReinforcementBonusByPlayer,
        )
    }

    @Test
    fun `cheat reinforcement bonus cannot be used twice by same player`() {
        val lobbyCode = LobbyCode("1073")
        val playerOne = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            )

        val withUsedBonus =
            reducer.apply(
                initialState,
                CheatReinforcementBonusUsedEvent(lobbyCode, playerOne),
            )

        /**
         * Auch wenn die Hauptprüfung im Server-Routing sitzt, schützt der Reducer
         * den State zusätzlich davor, dass dasselbe Event fachlich zweimal
         * angewendet wird.
         */
        val exception =
            assertThrows(InvalidLobbyEventException::class.java) {
                reducer.apply(
                    withUsedBonus,
                    CheatReinforcementBonusUsedEvent(lobbyCode, playerOne),
                )
            }

        assertEquals(
            "Spieler '1' hat den Schummel-Verstärkungsbonus bereits verwendet.",
            exception.message,
        )
    }

    @Test
    fun `pending reinforcements set und change aktualisieren state deterministisch`() {
        val lobbyCode = LobbyCode("1394")
        val playerOne = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            )

        val withSet =
            reducer.apply(
                initialState,
                PendingReinforcementsSetEvent(lobbyCode, playerOne, 5),
            )
        val withAdd =
            reducer.apply(
                withSet,
                PendingReinforcementsChangedEvent(lobbyCode, playerOne, 3),
            )
        val withSubtract =
            reducer.apply(
                withAdd,
                PendingReinforcementsChangedEvent(lobbyCode, playerOne, -2),
            )

        assertEquals(5, withSet.pendingReinforcementsFor(playerOne))
        assertEquals(8, withAdd.pendingReinforcementsFor(playerOne))
        assertEquals(6, withSubtract.pendingReinforcementsFor(playerOne))
    }

    @Test
    fun `pending reinforcements cannot go negative`() {
        val lobbyCode = LobbyCode("1396")
        val playerOne = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            )

        val withSet =
            reducer.apply(
                initialState,
                PendingReinforcementsSetEvent(lobbyCode, playerOne, 2),
            )

        val exception =
            assertThrows(InvalidLobbyEventException::class.java) {
                reducer.apply(
                    withSet,
                    PendingReinforcementsChangedEvent(lobbyCode, playerOne, -3),
                )
            }

        assertEquals(
            "PendingReinforcements für Spieler '1' dürfen nicht negativ werden: " +
                "aktuell=2, delta=-3.",
            exception.message,
        )
    }

    @Test
    fun `pending reinforcements change cannot overflow int`() {
        val lobbyCode = LobbyCode("1397")
        val playerOne = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            )

        val withSet =
            reducer.apply(
                initialState,
                PendingReinforcementsSetEvent(lobbyCode, playerOne, Int.MAX_VALUE),
            )

        val exception =
            assertThrows(InvalidLobbyEventException::class.java) {
                reducer.apply(
                    withSet,
                    PendingReinforcementsChangedEvent(lobbyCode, playerOne, 1),
                )
            }

        assertEquals(
            "PendingReinforcements für Spieler '1' dürfen den Int-Bereich nicht " +
                "verlassen: aktuell=2147483647, delta=1.",
            exception.message,
        )
    }

    @Test
    fun `card set traded in increments global count deterministically`() {
        val lobbyCode = LobbyCode("1398")
        val playerOne = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            )

        val firstTrade =
            reducer.apply(
                initialState,
                CardSetTradedInEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerOne,
                    cardIds = listOf(CardId("card-1"), CardId("card-2"), CardId("card-3")),
                    value = 2,
                    tradeIndex = 1,
                ),
            )
        val secondTrade =
            reducer.apply(
                firstTrade,
                CardSetTradedInEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerOne,
                    cardIds = listOf(CardId("card-4"), CardId("card-5"), CardId("card-6")),
                    value = 4,
                    tradeIndex = 2,
                ),
            )

        assertEquals(1, firstTrade.tradedInSetCount)
        assertEquals(2, secondTrade.tradedInSetCount)
    }

    @Test
    fun `card set traded in clears next reinforcement trade requirement`() {
        val lobbyCode = LobbyCode("1362")
        val playerOne = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            ).withTradeRequiredOnNextReinforcementPhase(playerOne, true)

        val updatedState =
            reducer.apply(
                initialState,
                CardSetTradedInEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerOne,
                    cardIds = listOf(CardId("card-1"), CardId("card-2"), CardId("card-3")),
                    value = 2,
                    tradeIndex = 1,
                ),
            )

        assertEquals(false, updatedState.tradeRequiredOnNextReinforcementPhaseFor(playerOne))
    }

    @Test
    fun `player cards removed event updates hand deterministically`() {
        val lobbyCode = LobbyCode("1399")
        val playerOne = PlayerId(1)
        val cardOne = CardState(CardId("card-1"), CardType.A)
        val cardTwo = CardState(CardId("card-2"), CardType.B)
        val cardThree = CardState(CardId("card-3"), CardType.JOKER)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            )
                .withCardAddedToHand(playerOne, cardOne)
                .withCardAddedToHand(playerOne, cardTwo)
                .withCardAddedToHand(playerOne, cardThree)

        val updatedState =
            reducer.apply(
                initialState,
                PlayerCardsRemovedEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerOne,
                    cardIds = listOf(cardOne.cardId, cardThree.cardId),
                ),
            )

        assertEquals(listOf(cardTwo), updatedState.handOf(playerOne))
        assertEquals(listOf(cardOne, cardThree), updatedState.discardPileState.cards)
    }

    @Test
    fun `territory event mit unknown territory fuehrt zu fail`() {
        val lobbyCode = LobbyCode("1395")
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
    fun `territory events require loaded map and known owner`() {
        val lobbyCode = LobbyCode("1399")
        val playerOne = PlayerId(1)

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                GameState.initial(lobbyCode),
                TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("alpha"), 2),
            )
        }

        val mappedState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                mappedState,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), PlayerId(99)),
            )
        }
    }

    @Test
    fun `start player configuration rejects invalid lobby lifecycle and requester`() {
        val lobbyCode = LobbyCode("1343")
        val owner = PlayerId(1)
        val playerTwo = PlayerId(2)
        val baseState =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, playerTwo),
                turnOrder = listOf(owner, playerTwo),
                activePlayer = owner,
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                baseState.copy(status = GameStatus.CLOSED),
                StartPlayerConfigured(lobbyCode, playerTwo, owner),
            )
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                baseState,
                StartPlayerConfigured(lobbyCode, playerTwo, requesterPlayerId = playerTwo),
            )
        }
    }

    @Test
    fun `game start rejects closed state and missing map`() {
        val lobbyCode = LobbyCode("1185")
        val owner = PlayerId(1)
        val playerTwo = PlayerId(2)
        val playerThree = PlayerId(3)
        val baseState =
            GameState(
                lobbyCode = lobbyCode,
                lobbyOwner = owner,
                players = listOf(owner, playerTwo, playerThree),
                turnOrder = listOf(owner, playerTwo, playerThree),
                activePlayer = owner,
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(baseState.copy(status = GameStatus.CLOSED), GameStarted(lobbyCode))
        }
        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(baseState, GameStarted(lobbyCode))
        }
    }

    @Test
    fun `turn state update rejects invalid start and pause players`() {
        val lobbyCode = LobbyCode("1414")
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

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                baseState,
                TurnStateUpdatedEvent(
                    lobbyCode = lobbyCode,
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.ATTACK,
                    turnCount = 1,
                    startPlayerId = PlayerId(99),
                ),
            )
        }
    }

    @Test
    fun `bonus query reagiert korrekt auf event sequenz`() {
        val lobbyCode = LobbyCode("1397")
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

    @Test
    fun `territory ownership finishes running game when one player owns all territories`() {
        val lobbyCode = LobbyCode("1427")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val runningState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne, playerTwo),
            ).copy(
                gameStarted = true,
                status = GameStatus.RUNNING,
            )

        val afterAlpha =
            reducer.apply(
                runningState,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), playerOne),
            )
        val afterBeta =
            reducer.apply(
                afterAlpha,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("beta"), playerOne),
            )
        val finishedState =
            reducer.apply(
                afterBeta,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("gamma"), playerOne),
            )

        assertEquals(GameStatus.FINISHED, finishedState.status)
    }

    @Test
    fun `card set traded in rejects wrong trade index and wrong value`() {
        val lobbyCode = LobbyCode("1400")
        val playerOne = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            )

        val wrongIndexException =
            assertThrows(InvalidLobbyEventException::class.java) {
                reducer.apply(
                    initialState,
                    CardSetTradedInEvent(
                        lobbyCode = lobbyCode,
                        playerId = playerOne,
                        cardIds = listOf(CardId("c1"), CardId("c2"), CardId("c3")),
                        value = 4,
                        tradeIndex = 2,
                    ),
                )
            }
        assertEquals(
            "CardSetTradedInEvent.tradeIndex muss den naechsten globalen " +
                "Trade-In abbilden: erwartet=1, war=2.",
            wrongIndexException.message,
        )

        val wrongValueException =
            assertThrows(InvalidLobbyEventException::class.java) {
                reducer.apply(
                    initialState,
                    CardSetTradedInEvent(
                        lobbyCode = lobbyCode,
                        playerId = playerOne,
                        cardIds = listOf(CardId("c1"), CardId("c2"), CardId("c3")),
                        value = 99,
                        tradeIndex = 1,
                    ),
                )
            }
        assertEquals(
            "CardSetTradedInEvent.value passt nicht zur Progression: erwartet=2, war=99.",
            wrongValueException.message,
        )
    }

    @Test
    fun `card set traded in rejects unknown player`() {
        val lobbyCode = LobbyCode("1401")
        val playerOne = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne),
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            reducer.apply(
                initialState,
                CardSetTradedInEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(99),
                    cardIds = listOf(CardId("c1"), CardId("c2"), CardId("c3")),
                    value = 2,
                    tradeIndex = 1,
                ),
            )
        }
    }

    @Test
    fun `pending reinforcements changed rejects cross player modification`() {
        val lobbyCode = LobbyCode("1402")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne, playerTwo),
            )
        val withSet =
            reducer.apply(
                initialState,
                PendingReinforcementsSetEvent(lobbyCode, playerOne, 5),
            )

        val exception =
            assertThrows(InvalidLobbyEventException::class.java) {
                reducer.apply(
                    withSet,
                    PendingReinforcementsChangedEvent(lobbyCode, playerTwo, 3),
                )
            }
        assertEquals(
            "PendingReinforcements gehören Spieler '1' und " +
                "können nicht für '2' verändert werden.",
            exception.message,
        )
    }

    private fun runningAttackState(
        lobbyCode: LobbyCode,
        attacker: PlayerId,
        defender: PlayerId,
    ): GameState =
        GameState.initial(
            lobbyCode = lobbyCode,
            mapDefinition = sampleMapDefinition(),
            players = listOf(attacker, defender),
        ).copy(
            activePlayer = attacker,
            turnOrder = listOf(attacker, defender),
            turnState =
                TurnState(
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    turnCount = 1,
                    startPlayerId = attacker,
                ),
            gameStarted = true,
            status = GameStatus.RUNNING,
            gameRandomSeed = 1L,
            gameRandomState = 2L,
            territoryStates =
                GameState.initial(
                    lobbyCode = lobbyCode,
                    mapDefinition = sampleMapDefinition(),
                    players = listOf(attacker, defender),
                ).allTerritoryStates().associate { territoryState ->
                    val territoryId = territoryState.territoryId
                    territoryId to
                        when (territoryId.value) {
                            "alpha" -> TerritoryState(territoryId, attacker, 5)
                            "beta" -> TerritoryState(territoryId, defender, 2)
                            else -> TerritoryState(territoryId, attacker, 1)
                        }
                },
        )

    private fun validAttackResolvedEvent(
        lobbyCode: LobbyCode,
        attacker: PlayerId,
        defender: PlayerId,
        attackerLosses: Int = 1,
        defenderLosses: Int = 1,
        attackerRemaining: Int = 4,
        defenderRemaining: Int = 1,
        occupyingTroopCount: Int? = null,
        minOccupyingTroops: Int? = null,
    ) = AttackResolvedEvent(
        lobbyCode = lobbyCode,
        attackerPlayerId = attacker,
        defenderPlayerId = defender,
        fromTerritoryId = TerritoryId("alpha"),
        toTerritoryId = TerritoryId("beta"),
        attackTroops = 3,
        sourceTroopsBefore = 5,
        targetTroopsBefore = 2,
        requestedAttackDice = 3,
        attackDice = 3,
        defendDice = 2,
        attackerRolls = listOf(6, 5, 4),
        defenderRolls = listOf(2, 1),
        rngTrace = listOf(6, 5, 4, 2, 1),
        rngStateBefore = 2L,
        rngStateAfter = 3L,
        attackerLosses = attackerLosses,
        defenderLosses = defenderLosses,
        attackerRemaining = attackerRemaining,
        defenderRemaining = defenderRemaining,
        occupyingTroopCount = occupyingTroopCount,
        minOccupyingTroops = minOccupyingTroops,
    )

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

    private fun defaultMapDefinition(): MapDefinition = MapConfigLoader.loadDefault()
}
