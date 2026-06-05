package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.HandState
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import at.aau.pulverfass.shared.message.lobby.request.AttackRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmAttackDoneRequest
import at.aau.pulverfass.shared.message.lobby.response.AttackResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmAttackDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class AttackIntegrationTest {
    @Test
    fun `happy path accepts one attack request and broadcasts resulting delta`() =
        testApplication {
            val lobbyCode = LobbyCode("ATK1")
            val attacker = PlayerId(1)
            val defender = PlayerId(2)
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId

            val initialState =
                attackGame(
                    lobbyCode = lobbyCode,
                    players = listOf(attacker, defender),
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    rngSeed = 1L,
                    rngState = 16L,
                    owners = mapOf(fromTerritoryId to attacker, toTerritoryId to defender),
                    troopCounts = mapOf(fromTerritoryId to 5, toTerritoryId to 2),
                )

            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val attackerSession =
                        fixture.connectPlayer(fixture.client, attacker)
                    val defenderSession =
                        fixture.connectPlayer(fixture.client, defender)

                    attackerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    AttackRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = attacker,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        moveAfterCapture = 3,
                                        requestId = "req-1",
                                    ),
                                ),
                        ),
                    )

                    val attackDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    AttackResolvedBroadcastEvent(
                                        lobbyCode = lobbyCode,
                                        attackerPlayerId = attacker,
                                        defenderPlayerId = defender,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        sourceTroopsBefore = 5,
                                        targetTroopsBefore = 2,
                                        requestedAttackDice = 3,
                                        attackDice = 3,
                                        defendDice = 2,
                                        attackerRolls = listOf(6, 5, 2),
                                        defenderRolls = listOf(5, 5),
                                        attackerLosses = 1,
                                        defenderLosses = 1,
                                        attackerRemaining = 4,
                                        defenderRemaining = 1,
                                        stateVersion = 1L,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = fromTerritoryId,
                                        troopCount = 4,
                                        stateVersion = 1,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = toTerritoryId,
                                        troopCount = 1,
                                        stateVersion = 1,
                                    ),
                                ),
                        )

                    assertEquals(attackDelta, receiveRelevantTestPayload(attackerSession.first))
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "req-1"),
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(attackDelta, receiveRelevantTestPayload(defenderSession.first))
                    assertNull(
                        receiveRelevantTestPayloadOrNull(
                            session = attackerSession.first,
                            timeoutMillis = 3_000,
                        ),
                    )
                    assertNull(receiveRelevantTestPayloadOrNull(defenderSession.first))

                    val updatedState =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(4, updatedState.troopCountOf(fromTerritoryId))
                    assertEquals(1, updatedState.troopCountOf(toTerritoryId))
                    assertEquals(1L, updatedState.stateVersion)
                    assertEquals(TurnPhase.ATTACK, updatedState.activeTurnPhase)

                    attackerSession.first.close()
                    defenderSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `attack flow integrates manual phase end through router service and runtime`() =
        testApplication {
            val lobbyCode = LobbyCode("AT12")
            val attacker = PlayerId(1)
            val defender = PlayerId(2)
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId

            val initialState =
                attackGame(
                    lobbyCode = lobbyCode,
                    players = listOf(attacker, defender),
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    rngSeed = 1L,
                    rngState = 16L,
                    owners = mapOf(fromTerritoryId to attacker, toTerritoryId to defender),
                    troopCounts = mapOf(fromTerritoryId to 5, toTerritoryId to 2),
                )

            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val attackerSession = fixture.connectPlayer(fixture.client, attacker)
                    val defenderSession = fixture.connectPlayer(fixture.client, defender)

                    attackerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    AttackRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = attacker,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        moveAfterCapture = 3,
                                        requestId = "req-flow-attack",
                                    ),
                                ),
                        ),
                    )

                    val attackDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    AttackResolvedBroadcastEvent(
                                        lobbyCode = lobbyCode,
                                        attackerPlayerId = attacker,
                                        defenderPlayerId = defender,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        sourceTroopsBefore = 5,
                                        targetTroopsBefore = 2,
                                        requestedAttackDice = 3,
                                        attackDice = 3,
                                        defendDice = 2,
                                        attackerRolls = listOf(6, 5, 2),
                                        defenderRolls = listOf(5, 5),
                                        attackerLosses = 1,
                                        defenderLosses = 1,
                                        attackerRemaining = 4,
                                        defenderRemaining = 1,
                                        stateVersion = 1L,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = fromTerritoryId,
                                        troopCount = 4,
                                        stateVersion = 1,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = toTerritoryId,
                                        troopCount = 1,
                                        stateVersion = 1,
                                    ),
                                ),
                        )

                    assertEquals(attackDelta, receiveRelevantTestPayload(attackerSession.first))
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "req-flow-attack"),
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(attackDelta, receiveRelevantTestPayload(defenderSession.first))

                    attackerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ConfirmAttackDoneRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = attacker,
                                    ),
                                ),
                        ),
                    )

                    val fortifyUpdate =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = attacker,
                            turnPhase = TurnPhase.FORTIFY,
                            turnCount = 1,
                            startPlayerId = attacker,
                        )
                    val confirmDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 2,
                            toVersion = 2,
                            events = listOf(fortifyUpdate),
                        )
                    val phaseBoundary =
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 2,
                            previousPhase = TurnPhase.ATTACK,
                            nextPhase = TurnPhase.FORTIFY,
                            activePlayerId = attacker,
                            turnCount = 1,
                        )

                    assertEquals(confirmDelta, receiveRelevantTestPayload(attackerSession.first))
                    assertEquals(
                        ConfirmAttackDoneResponse(lobbyCode),
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(phaseBoundary, receiveRelevantTestPayload(attackerSession.first))
                    assertEquals(fortifyUpdate, receiveRelevantTestPayload(attackerSession.first))

                    assertEquals(confirmDelta, receiveRelevantTestPayload(defenderSession.first))
                    assertEquals(phaseBoundary, receiveRelevantTestPayload(defenderSession.first))
                    assertEquals(fortifyUpdate, receiveRelevantTestPayload(defenderSession.first))

                    val updatedState =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(TurnPhase.FORTIFY, updatedState.activeTurnPhase)
                    assertEquals(2L, updatedState.stateVersion)
                    assertNull(receiveRelevantTestPayloadOrNull(attackerSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(defenderSession.first))

                    attackerSession.first.close()
                    defenderSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `attack resolved details are public to uninvolved watcher`() =
        testApplication {
            val lobbyCode = LobbyCode("AT13")
            val attacker = PlayerId(1)
            val defender = PlayerId(2)
            val watcher = PlayerId(3)
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId
            val watcherTerritoryId =
                map.territories
                    .map(TerritoryDefinition::territoryId)
                    .first { it != fromTerritoryId && it != toTerritoryId }

            val initialState =
                attackGame(
                    lobbyCode = lobbyCode,
                    players = listOf(attacker, defender, watcher),
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    rngSeed = 1L,
                    rngState = 16L,
                    owners =
                        mapOf(
                            fromTerritoryId to attacker,
                            toTerritoryId to defender,
                            watcherTerritoryId to watcher,
                        ),
                    troopCounts =
                        mapOf(
                            fromTerritoryId to 5,
                            toTerritoryId to 2,
                            watcherTerritoryId to 1,
                        ),
                    mapDefinition = map,
                )

            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val attackerSession = fixture.connectPlayer(fixture.client, attacker)
                    val defenderSession = fixture.connectPlayer(fixture.client, defender)
                    val watcherSession = fixture.connectPlayer(fixture.client, watcher)

                    attackerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    AttackRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = attacker,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        moveAfterCapture = 3,
                                        requestId = "req-public-scope",
                                    ),
                                ),
                        ),
                    )

                    val attackDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    AttackResolvedBroadcastEvent(
                                        lobbyCode = lobbyCode,
                                        attackerPlayerId = attacker,
                                        defenderPlayerId = defender,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        sourceTroopsBefore = 5,
                                        targetTroopsBefore = 2,
                                        requestedAttackDice = 3,
                                        attackDice = 3,
                                        defendDice = 2,
                                        attackerRolls = listOf(6, 5, 2),
                                        defenderRolls = listOf(5, 5),
                                        attackerLosses = 1,
                                        defenderLosses = 1,
                                        attackerRemaining = 4,
                                        defenderRemaining = 1,
                                        stateVersion = 1L,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = fromTerritoryId,
                                        troopCount = 4,
                                        stateVersion = 1,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = toTerritoryId,
                                        troopCount = 1,
                                        stateVersion = 1,
                                    ),
                                ),
                        )

                    assertEquals(attackDelta, receiveRelevantTestPayload(attackerSession.first))
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "req-public-scope"),
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(attackDelta, receiveRelevantTestPayload(defenderSession.first))
                    assertEquals(attackDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(attackerSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(defenderSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(watcherSession.first))

                    attackerSession.first.close()
                    defenderSession.first.close()
                    watcherSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `capture transfers owner and moves troops according to moveAfterCapture`() =
        testApplication {
            val lobbyCode = LobbyCode("ATK7")
            val attacker = PlayerId(1)
            val defender = PlayerId(2)
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId

            val initialState =
                attackGame(
                    lobbyCode = lobbyCode,
                    players = listOf(attacker, defender),
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    rngSeed = 1L,
                    rngState = 2L,
                    owners = mapOf(fromTerritoryId to attacker, toTerritoryId to defender),
                    troopCounts = mapOf(fromTerritoryId to 5, toTerritoryId to 2),
                )

            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val attackerSession = fixture.connectPlayer(fixture.client, attacker)
                    val defenderSession = fixture.connectPlayer(fixture.client, defender)

                    attackerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    AttackRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = attacker,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        moveAfterCapture = 3,
                                        requestId = "req-capture",
                                    ),
                                ),
                        ),
                    )

                    val delta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    AttackResolvedBroadcastEvent(
                                        lobbyCode = lobbyCode,
                                        attackerPlayerId = attacker,
                                        defenderPlayerId = defender,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        sourceTroopsBefore = 5,
                                        targetTroopsBefore = 2,
                                        requestedAttackDice = 3,
                                        attackDice = 3,
                                        defendDice = 2,
                                        attackerRolls = listOf(5, 4, 3),
                                        defenderRolls = listOf(2, 1),
                                        attackerLosses = 0,
                                        defenderLosses = 2,
                                        attackerRemaining = 5,
                                        defenderRemaining = 0,
                                        occupyingTroopCount = 3,
                                        minOccupyingTroops = 3,
                                        stateVersion = 1L,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = fromTerritoryId,
                                        troopCount = 2,
                                        stateVersion = 1,
                                    ),
                                    TerritoryOwnerChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = toTerritoryId,
                                        ownerId = attacker,
                                        stateVersion = 1,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = toTerritoryId,
                                        troopCount = 3,
                                        stateVersion = 1,
                                    ),
                                ),
                        )
                    val eliminationDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 2,
                            toVersion = 2,
                            events =
                                listOf(
                                    PlayerEliminatedEvent(
                                        lobbyCode = lobbyCode,
                                        playerId = defender,
                                        eliminatedByPlayerId = attacker,
                                        stateVersion = 2L,
                                    ),
                                ),
                        )

                    assertEquals(delta, receiveRelevantTestPayload(attackerSession.first))
                    assertEquals(
                        eliminationDelta,
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "req-capture"),
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(delta, receiveRelevantTestPayload(defenderSession.first))
                    assertEquals(
                        eliminationDelta,
                        receiveRelevantTestPayload(defenderSession.first),
                    )
                    assertNull(receiveRelevantTestPayloadOrNull(attackerSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(defenderSession.first))

                    val updatedState =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(attacker, updatedState.ownerOf(toTerritoryId))
                    assertEquals(2, updatedState.troopCountOf(fromTerritoryId))
                    assertEquals(3, updatedState.troopCountOf(toTerritoryId))
                    assertEquals(listOf(attacker), updatedState.turnOrder)
                    assertEquals(true, updatedState.isSpectator(defender))
                    assertEquals(2L, updatedState.stateVersion)

                    attackerSession.first.close()
                    defenderSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `attack can capture territory owned by player who left the lobby`() =
        testApplication {
            val lobbyCode = LobbyCode("ATKX")
            val attacker = PlayerId(1)
            val bystander = PlayerId(2)
            val departedDefender = PlayerId(99)
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId

            val initialState =
                attackGame(
                    lobbyCode = lobbyCode,
                    players = listOf(attacker, bystander),
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    rngSeed = 1L,
                    rngState = 2L,
                    owners = mapOf(fromTerritoryId to attacker, toTerritoryId to departedDefender),
                    troopCounts = mapOf(fromTerritoryId to 6, toTerritoryId to 1),
                )
            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val attackerSession = fixture.connectPlayer(fixture.client, attacker)

                    attackerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    AttackRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = attacker,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 5,
                                        moveAfterCapture = 5,
                                        requestId = "req-departed-capture",
                                    ),
                                ),
                        ),
                    )

                    val delta =
                        receiveRelevantTestPayload(attackerSession.first) as GameStateDeltaEvent
                    val resolved =
                        delta.events
                            .filterIsInstance<AttackResolvedBroadcastEvent>()
                            .single()
                    assertEquals(departedDefender, resolved.defenderPlayerId)
                    assertEquals(true, resolved.capture)
                    assertEquals(5, resolved.attackTroops)
                    assertEquals(1, resolved.targetTroopsBefore)
                    assertEquals(
                        AttackResponse(
                            lobbyCode = lobbyCode,
                            requestId = "req-departed-capture",
                        ),
                        receiveRelevantTestPayload(attackerSession.first),
                    )

                    val snapshot =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("snapshot missing")
                    assertEquals(attacker, snapshot.ownerOf(toTerritoryId))

                    attackerSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `elimination keeps hand updates private and phase changes public`() =
        testApplication {
            val lobbyCode = LobbyCode("AT14")
            val attacker = PlayerId(1)
            val defender = PlayerId(2)
            val watcher = PlayerId(3)
            val fromTerritoryId = TerritoryId("alpha")
            val toTerritoryId = TerritoryId("beta")
            val attackerCards =
                listOf(
                    CardState(CardId("a-1"), CardType.A),
                    CardState(CardId("b-1"), CardType.B),
                )
            val defenderCards =
                listOf(
                    CardState(CardId("c-1"), CardType.C),
                )
            val watcherCards =
                listOf(
                    CardState(CardId("w-1"), CardType.JOKER),
                )

            val initialState =
                attackGame(
                    lobbyCode = lobbyCode,
                    players = listOf(attacker, defender, watcher),
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    rngSeed = 1L,
                    rngState = 2L,
                    owners =
                        mapOf(
                            fromTerritoryId to attacker,
                            toTerritoryId to defender,
                            TerritoryId("gamma") to watcher,
                        ),
                    troopCounts =
                        mapOf(
                            fromTerritoryId to 5,
                            toTerritoryId to 2,
                            TerritoryId("gamma") to 1,
                        ),
                    mapDefinition = isolatedFallbackMapDefinition(),
                ).copy(
                    handState =
                        HandState(
                            cardsByPlayer =
                                mapOf(
                                    attacker to attackerCards,
                                    defender to defenderCards,
                                    watcher to watcherCards,
                                ),
                        ),
                )

            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val attackerSession = fixture.connectPlayer(fixture.client, attacker)
                    val defenderSession = fixture.connectPlayer(fixture.client, defender)
                    val watcherSession = fixture.connectPlayer(fixture.client, watcher)

                    attackerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    AttackRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = attacker,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        moveAfterCapture = 3,
                                        requestId = "req-private-scope",
                                    ),
                                ),
                        ),
                    )

                    val captureDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    AttackResolvedBroadcastEvent(
                                        lobbyCode = lobbyCode,
                                        attackerPlayerId = attacker,
                                        defenderPlayerId = defender,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        sourceTroopsBefore = 5,
                                        targetTroopsBefore = 2,
                                        requestedAttackDice = 3,
                                        attackDice = 3,
                                        defendDice = 2,
                                        attackerRolls = listOf(5, 4, 3),
                                        defenderRolls = listOf(2, 1),
                                        attackerLosses = 0,
                                        defenderLosses = 2,
                                        attackerRemaining = 5,
                                        defenderRemaining = 0,
                                        occupyingTroopCount = 3,
                                        minOccupyingTroops = 3,
                                        stateVersion = 1L,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = fromTerritoryId,
                                        troopCount = 2,
                                        stateVersion = 1,
                                    ),
                                    TerritoryOwnerChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = toTerritoryId,
                                        ownerId = attacker,
                                        stateVersion = 1,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = toTerritoryId,
                                        troopCount = 3,
                                        stateVersion = 1,
                                    ),
                                ),
                        )
                    val eliminationDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 2,
                            toVersion = 2,
                            events =
                                listOf(
                                    PlayerEliminatedEvent(
                                        lobbyCode = lobbyCode,
                                        playerId = defender,
                                        eliminatedByPlayerId = attacker,
                                        stateVersion = 2L,
                                    ),
                                ),
                        )
                    val autoAdvanceDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 3,
                            toVersion = 3,
                            events =
                                listOf(
                                    TurnStateUpdatedEvent(
                                        lobbyCode = lobbyCode,
                                        activePlayerId = attacker,
                                        turnPhase = TurnPhase.FORTIFY,
                                        turnCount = 1,
                                        startPlayerId = attacker,
                                    ),
                                ),
                        )
                    val phaseBoundary =
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 3,
                            previousPhase = TurnPhase.ATTACK,
                            nextPhase = TurnPhase.FORTIFY,
                            activePlayerId = attacker,
                            turnCount = 1,
                        )
                    val fortifyTurnState =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = attacker,
                            turnPhase = TurnPhase.FORTIFY,
                            turnCount = 1,
                            startPlayerId = attacker,
                        )

                    assertEquals(captureDelta, receiveRelevantTestPayload(attackerSession.first))
                    assertEquals(
                        eliminationDelta,
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "req-private-scope"),
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(
                        PlayerHandUpdatedEvent(
                            lobbyCode = lobbyCode,
                            recipientPlayerId = attacker,
                            stateVersion = 2,
                            handCards =
                                listOf(
                                    PrivateHandCardSnapshot(CardId("a-1"), CardType.A),
                                    PrivateHandCardSnapshot(CardId("b-1"), CardType.B),
                                    PrivateHandCardSnapshot(CardId("c-1"), CardType.C),
                                ),
                        ),
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(
                        autoAdvanceDelta,
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(phaseBoundary, receiveRelevantTestPayload(attackerSession.first))
                    assertEquals(
                        fortifyTurnState,
                        receiveRelevantTestPayload(attackerSession.first),
                    )

                    assertEquals(captureDelta, receiveRelevantTestPayload(defenderSession.first))
                    assertEquals(
                        eliminationDelta,
                        receiveRelevantTestPayload(defenderSession.first),
                    )
                    assertEquals(
                        PlayerHandUpdatedEvent(
                            lobbyCode = lobbyCode,
                            recipientPlayerId = defender,
                            stateVersion = 2,
                            handCards = emptyList(),
                        ),
                        receiveRelevantTestPayload(defenderSession.first),
                    )
                    assertEquals(
                        autoAdvanceDelta,
                        receiveRelevantTestPayload(defenderSession.first),
                    )
                    assertEquals(phaseBoundary, receiveRelevantTestPayload(defenderSession.first))
                    assertEquals(
                        fortifyTurnState,
                        receiveRelevantTestPayload(defenderSession.first),
                    )

                    assertEquals(captureDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertEquals(eliminationDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertEquals(autoAdvanceDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertEquals(phaseBoundary, receiveRelevantTestPayload(watcherSession.first))
                    assertEquals(fortifyTurnState, receiveRelevantTestPayload(watcherSession.first))

                    assertNull(receiveRelevantTestPayloadOrNull(attackerSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(defenderSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(watcherSession.first))

                    val updatedState =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(attackerCards + defenderCards, updatedState.handOf(attacker))
                    assertEquals(emptyList<CardState>(), updatedState.handOf(defender))
                    assertEquals(watcherCards, updatedState.handOf(watcher))
                    assertEquals(TurnPhase.FORTIFY, updatedState.activeTurnPhase)

                    attackerSession.first.close()
                    defenderSession.first.close()
                    watcherSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `attack is rejected when moveAfterCapture would violate capture invariants`() =
        testApplication {
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val (error, snapshot) =
                exerciseFailingAttack(
                    builder = this,
                    lobbyCode = LobbyCode("ATK8"),
                    state =
                        attackGame(
                            lobbyCode = LobbyCode("ATK8"),
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            rngSeed = 1L,
                            rngState = 2L,
                            owners =
                                mapOf(
                                    fromTerritoryId to playerOne,
                                    toTerritoryId to playerTwo,
                                ),
                            troopCounts = mapOf(fromTerritoryId to 5, toTerritoryId to 2),
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        AttackRequest(
                            lobbyCode = LobbyCode("ATK8"),
                            playerId = playerOne,
                            fromTerritoryId = fromTerritoryId,
                            toTerritoryId = toTerritoryId,
                            attackTroops = 3,
                            moveAfterCapture = 5,
                        ),
                )

            assertEquals(AttackErrorCode.INVALID_MOVE_AFTER_CAPTURE, error.code)
            assertEquals(playerTwo, snapshot.ownerOf(toTerritoryId))
            assertEquals(5, snapshot.troopCountOf(fromTerritoryId))
            assertEquals(2, snapshot.troopCountOf(toTerritoryId))
            assertEquals(0L, snapshot.stateVersion)
        }

    @Test
    fun `attack auto advances to fortify when no valid attacks remain`() =
        testApplication {
            val lobbyCode = LobbyCode("AT11")
            val attacker = PlayerId(1)
            val defender = PlayerId(2)
            val map = isolatedFallbackMapDefinition()
            val fromTerritoryId = TerritoryId("alpha")
            val toTerritoryId = TerritoryId("beta")

            val initialState =
                attackGame(
                    lobbyCode = lobbyCode,
                    players = listOf(attacker, defender),
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    rngSeed = 1L,
                    rngState = 2L,
                    owners =
                        mapOf(
                            fromTerritoryId to attacker,
                            toTerritoryId to defender,
                            TerritoryId("gamma") to defender,
                        ),
                    troopCounts =
                        mapOf(
                            fromTerritoryId to 5,
                            toTerritoryId to 2,
                            TerritoryId("gamma") to 1,
                        ),
                    mapDefinition = map,
                )

            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val attackerSession = fixture.connectPlayer(fixture.client, attacker)
                    val watcherSession = fixture.connectPlayer(fixture.client, defender)

                    attackerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    AttackRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = attacker,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        moveAfterCapture = 3,
                                        requestId = "req-auto-end",
                                    ),
                                ),
                        ),
                    )

                    val attackDelta = receiveRelevantTestPayload(attackerSession.first)
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "req-auto-end"),
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(attackDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(attackerSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(watcherSession.first))

                    val autoAdvanceDelta = receiveRelevantTestPayload(attackerSession.first)
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 2,
                            previousPhase = TurnPhase.ATTACK,
                            nextPhase = TurnPhase.FORTIFY,
                            activePlayerId = attacker,
                            turnCount = 1,
                        ),
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = attacker,
                            turnPhase = TurnPhase.FORTIFY,
                            turnCount = 1,
                            startPlayerId = attacker,
                        ),
                        receiveRelevantTestPayload(attackerSession.first),
                    )

                    assertEquals(autoAdvanceDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 2,
                            previousPhase = TurnPhase.ATTACK,
                            nextPhase = TurnPhase.FORTIFY,
                            activePlayerId = attacker,
                            turnCount = 1,
                        ),
                        receiveRelevantTestPayload(watcherSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = attacker,
                            turnPhase = TurnPhase.FORTIFY,
                            turnCount = 1,
                            startPlayerId = attacker,
                        ),
                        receiveRelevantTestPayload(watcherSession.first),
                    )

                    val updatedState =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(TurnPhase.FORTIFY, updatedState.activeTurnPhase)
                    assertEquals(false, updatedState.hasAnyValidAttack(attacker))

                    attackerSession.first.close()
                    watcherSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `eliminated player attack request is denied while spectator stays connected`() =
        testApplication {
            val lobbyCode = LobbyCode("ATK9")
            val attacker = PlayerId(1)
            val defender = PlayerId(2)
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId

            val initialState =
                attackGame(
                    lobbyCode = lobbyCode,
                    players = listOf(attacker, defender),
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    rngSeed = 1L,
                    rngState = 2L,
                    owners = mapOf(fromTerritoryId to attacker, toTerritoryId to defender),
                    troopCounts = mapOf(fromTerritoryId to 5, toTerritoryId to 2),
                )

            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val attackerSession = fixture.connectPlayer(fixture.client, attacker)
                    val defenderSession = fixture.connectPlayer(fixture.client, defender)

                    attackerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    AttackRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = attacker,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        moveAfterCapture = 3,
                                        requestId = "req-elim",
                                    ),
                                ),
                        ),
                    )

                    receiveRelevantTestPayload(attackerSession.first)
                    receiveRelevantTestPayload(attackerSession.first)
                    receiveRelevantTestPayload(attackerSession.first)
                    receiveRelevantTestPayload(defenderSession.first)
                    receiveRelevantTestPayload(defenderSession.first)

                    defenderSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    AttackRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = defender,
                                        fromTerritoryId = toTerritoryId,
                                        toTerritoryId = fromTerritoryId,
                                        attackTroops = 2,
                                        moveAfterCapture = 2,
                                        requestId = "req-denied",
                                    ),
                                ),
                        ),
                    )

                    val error =
                        receiveRelevantTestPayload(defenderSession.first) as AttackErrorResponse
                    assertEquals(AttackErrorCode.NOT_ACTIVE_PLAYER, error.code)
                    assertEquals("req-denied", error.requestId)

                    val updatedState =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(true, updatedState.isSpectator(defender))
                    assertEquals(listOf(attacker), updatedState.turnOrder)
                    assertEquals(attacker, updatedState.ownerOf(toTerritoryId))

                    attackerSession.first.close()
                    defenderSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `elimination transfers cards and defers forced trade to reinforcement`() =
        testApplication {
            val lobbyCode = LobbyCode("AK10")
            val attacker = PlayerId(1)
            val defender = PlayerId(2)
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId
            val attackerCards =
                listOf(
                    CardState(CardId("a-1"), CardType.A),
                    CardState(CardId("b-1"), CardType.B),
                )
            val defenderCards =
                listOf(
                    CardState(CardId("c-1"), CardType.C),
                    CardState(CardId("j-1"), CardType.JOKER),
                    CardState(CardId("a-2"), CardType.A),
                )

            val initialState =
                attackGame(
                    lobbyCode = lobbyCode,
                    players = listOf(attacker, defender),
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    rngSeed = 1L,
                    rngState = 2L,
                    owners = mapOf(fromTerritoryId to attacker, toTerritoryId to defender),
                    troopCounts = mapOf(fromTerritoryId to 5, toTerritoryId to 2),
                ).copy(
                    handState =
                        HandState(
                            cardsByPlayer =
                                mapOf(
                                    attacker to attackerCards,
                                    defender to defenderCards,
                                ),
                        ),
                )

            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val attackerSession = fixture.connectPlayer(fixture.client, attacker)
                    val defenderSession = fixture.connectPlayer(fixture.client, defender)

                    attackerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    AttackRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = attacker,
                                        fromTerritoryId = fromTerritoryId,
                                        toTerritoryId = toTerritoryId,
                                        attackTroops = 3,
                                        moveAfterCapture = 3,
                                        requestId = "req-cards",
                                    ),
                                ),
                        ),
                    )

                    receiveRelevantTestPayload(attackerSession.first)
                    receiveRelevantTestPayload(attackerSession.first)
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "req-cards"),
                        receiveRelevantTestPayload(attackerSession.first),
                    )
                    assertEquals(
                        PlayerHandUpdatedEvent(
                            lobbyCode = lobbyCode,
                            recipientPlayerId = attacker,
                            stateVersion = 2,
                            handCards =
                                listOf(
                                    PrivateHandCardSnapshot(CardId("a-1"), CardType.A),
                                    PrivateHandCardSnapshot(CardId("b-1"), CardType.B),
                                    PrivateHandCardSnapshot(CardId("c-1"), CardType.C),
                                    PrivateHandCardSnapshot(CardId("j-1"), CardType.JOKER),
                                    PrivateHandCardSnapshot(CardId("a-2"), CardType.A),
                                ),
                        ),
                        receiveRelevantTestPayload(attackerSession.first),
                    )

                    receiveRelevantTestPayload(defenderSession.first)
                    receiveRelevantTestPayload(defenderSession.first)
                    assertEquals(
                        PlayerHandUpdatedEvent(
                            lobbyCode = lobbyCode,
                            recipientPlayerId = defender,
                            stateVersion = 2,
                            handCards = emptyList(),
                        ),
                        receiveRelevantTestPayload(defenderSession.first),
                    )

                    val updatedState =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(attackerCards + defenderCards, updatedState.handOf(attacker))
                    assertEquals(emptyList<CardState>(), updatedState.handOf(defender))
                    assertEquals(
                        true,
                        updatedState.tradeRequiredOnNextReinforcementPhaseFor(attacker),
                    )
                    assertEquals(TurnPhase.ATTACK, updatedState.activeTurnPhase)
                    assertEquals(true, updatedState.isSpectator(defender))

                    attackerSession.first.close()
                    defenderSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `attack is rejected when requester is not active player`() =
        testApplication {
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val (error, snapshot) =
                exerciseFailingAttack(
                    builder = this,
                    lobbyCode = LobbyCode("ATK2"),
                    state =
                        attackGame(
                            lobbyCode = LobbyCode("ATK2"),
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.ATTACK,
                            rngSeed = 1L,
                            rngState = 16L,
                            owners =
                                mapOf(
                                    fromTerritoryId to playerOne,
                                    toTerritoryId to playerTwo,
                                ),
                            troopCounts = mapOf(fromTerritoryId to 5, toTerritoryId to 2),
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        AttackRequest(
                            lobbyCode = LobbyCode("ATK2"),
                            playerId = playerOne,
                            fromTerritoryId = fromTerritoryId,
                            toTerritoryId = toTerritoryId,
                            attackTroops = 3,
                            moveAfterCapture = 3,
                        ),
                )

            assertEquals(AttackErrorCode.NOT_ACTIVE_PLAYER, error.code)
            assertEquals(0L, snapshot.stateVersion)
        }

    @Test
    fun `attack is rejected when phase is not attack`() =
        testApplication {
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val (error, snapshot) =
                exerciseFailingAttack(
                    builder = this,
                    lobbyCode = LobbyCode("ATK3"),
                    state =
                        attackGame(
                            lobbyCode = LobbyCode("ATK3"),
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            rngSeed = 1L,
                            rngState = 16L,
                            owners =
                                mapOf(
                                    fromTerritoryId to playerOne,
                                    toTerritoryId to playerTwo,
                                ),
                            troopCounts = mapOf(fromTerritoryId to 5, toTerritoryId to 2),
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        AttackRequest(
                            lobbyCode = LobbyCode("ATK3"),
                            playerId = playerOne,
                            fromTerritoryId = fromTerritoryId,
                            toTerritoryId = toTerritoryId,
                            attackTroops = 3,
                            moveAfterCapture = 3,
                        ),
                )

            assertEquals(AttackErrorCode.PHASE_MISMATCH, error.code)
            assertEquals(0L, snapshot.stateVersion)
        }

    @Test
    fun `attack is rejected when territories are not adjacent`() =
        testApplication {
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = nonAdjacentTerritoryId(map, fromTerritoryId)
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val (error, snapshot) =
                exerciseFailingAttack(
                    builder = this,
                    lobbyCode = LobbyCode("ATK4"),
                    state =
                        attackGame(
                            lobbyCode = LobbyCode("ATK4"),
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            rngSeed = 1L,
                            rngState = 16L,
                            owners =
                                mapOf(
                                    fromTerritoryId to playerOne,
                                    toTerritoryId to playerTwo,
                                ),
                            troopCounts = mapOf(fromTerritoryId to 5, toTerritoryId to 2),
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        AttackRequest(
                            lobbyCode = LobbyCode("ATK4"),
                            playerId = playerOne,
                            fromTerritoryId = fromTerritoryId,
                            toTerritoryId = toTerritoryId,
                            attackTroops = 3,
                            moveAfterCapture = 3,
                        ),
                )

            assertEquals(AttackErrorCode.NOT_ADJACENT, error.code)
            assertEquals(0L, snapshot.stateVersion)
        }

    @Test
    fun `attack is rejected when troops are insufficient`() =
        testApplication {
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val (error, snapshot) =
                exerciseFailingAttack(
                    builder = this,
                    lobbyCode = LobbyCode("ATK5"),
                    state =
                        attackGame(
                            lobbyCode = LobbyCode("ATK5"),
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            rngSeed = 1L,
                            rngState = 16L,
                            owners =
                                mapOf(
                                    fromTerritoryId to playerOne,
                                    toTerritoryId to playerTwo,
                                ),
                            troopCounts = mapOf(fromTerritoryId to 3, toTerritoryId to 2),
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        AttackRequest(
                            lobbyCode = LobbyCode("ATK5"),
                            playerId = playerOne,
                            fromTerritoryId = fromTerritoryId,
                            toTerritoryId = toTerritoryId,
                            attackTroops = 3,
                            moveAfterCapture = 3,
                        ),
                )

            assertEquals(AttackErrorCode.INSUFFICIENT_TROOPS, error.code)
            assertEquals(0L, snapshot.stateVersion)
        }

    @Test
    fun `attack is rejected when attacking own territory`() =
        testApplication {
            val map = defaultMapDefinition()
            val fromTerritoryId = map.territories.first().territoryId
            val toTerritoryId = map.territories.first().edges.first().targetId
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val (error, snapshot) =
                exerciseFailingAttack(
                    builder = this,
                    lobbyCode = LobbyCode("ATK6"),
                    state =
                        attackGame(
                            lobbyCode = LobbyCode("ATK6"),
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            rngSeed = 1L,
                            rngState = 16L,
                            owners =
                                mapOf(
                                    fromTerritoryId to playerOne,
                                    toTerritoryId to playerOne,
                                ),
                            troopCounts = mapOf(fromTerritoryId to 5, toTerritoryId to 2),
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        AttackRequest(
                            lobbyCode = LobbyCode("ATK6"),
                            playerId = playerOne,
                            fromTerritoryId = fromTerritoryId,
                            toTerritoryId = toTerritoryId,
                            attackTroops = 3,
                            moveAfterCapture = 3,
                        ),
                )

            assertEquals(AttackErrorCode.ATTACKING_OWN_TERRITORY, error.code)
            assertEquals(0L, snapshot.stateVersion)
        }

    private suspend fun exerciseFailingAttack(
        builder: ApplicationTestBuilder,
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: AttackRequest,
    ): Pair<AttackErrorResponse, GameState> {
        val fixture = createFixture(lobbyCode, state, builder)

        return try {
            coroutineScope {
                val requesterSession = fixture.connectPlayer(fixture.client, requesterPlayerId)

                requesterSession.first.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(request),
                    ),
                )

                val error =
                    receiveRelevantTestPayload(
                        requesterSession.first,
                    ) as AttackErrorResponse
                assertNull(receiveRelevantTestPayloadOrNull(requesterSession.first))

                val snapshot =
                    fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                        ?: error("snapshot missing")
                requesterSession.first.close()
                error to snapshot
            }
        } finally {
            fixture.stop()
        }
    }

    private fun createFixture(
        lobbyCode: LobbyCode,
        initialState: GameState,
        builder: ApplicationTestBuilder,
    ): AttackFixture {
        val network = ServerNetwork()
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lobbyManager = LobbyManager(serverScope)
        val router =
            MainServerRouter(
                lobbyManager = lobbyManager,
                mapper = DefaultNetworkToLobbyEventMapper(),
            )
        val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
        val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
        val routingService =
            MainServerLobbyRoutingService(
                network = network,
                router = router,
                lobbyManager = lobbyManager,
                playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
                hooks = MainServerLobbyRoutingServiceHooks(),
            )

        builder.application {
            module(network)
        }
        lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = initialState)
        routingService.start(serverScope)

        return AttackFixture(
            client =
                builder.createClient {
                    install(WebSockets)
                },
            network = network,
            serverScope = serverScope,
            lobbyManager = lobbyManager,
            routingService = routingService,
            playersByConnection = playersByConnection,
            connectionsByPlayer = connectionsByPlayer,
        )
    }

    private fun attackGame(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        turnPhase: TurnPhase,
        rngSeed: Long,
        rngState: Long,
        owners: Map<TerritoryId, PlayerId>,
        troopCounts: Map<TerritoryId, Int>,
        mapDefinition: MapDefinition = defaultMapDefinition(),
    ): GameState {
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = mapDefinition,
                players = players,
                playerDisplayNames = players.associateWith { "Player ${it.value}" },
            )

        return baseState.copy(
            lobbyOwner = players.firstOrNull(),
            activePlayer = activePlayerId,
            turnOrder = players,
            turnNumber = 1,
            turnState =
                TurnState(
                    activePlayerId = activePlayerId,
                    turnPhase = turnPhase,
                    turnCount = 1,
                    startPlayerId = players.first(),
                ),
            gameStarted = true,
            status = GameStatus.RUNNING,
            gameRandomSeed = rngSeed,
            gameRandomState = rngState,
            territoryStates =
                baseState.allTerritoryStates().associate { territoryState ->
                    val territoryId = territoryState.territoryId
                    territoryId to
                        TerritoryState(
                            territoryId = territoryId,
                            ownerId = owners[territoryId],
                            troopCount = troopCounts[territoryId] ?: 0,
                        )
                },
        )
    }

    private suspend fun AttackFixture.connectPlayer(
        client: io.ktor.client.HttpClient,
        playerId: PlayerId,
    ) = coroutineScope {
        val session = client.webSocketSession("/ws")
        val sessionToken = receiveTestConnectionToken(session)
        val connectionId = awaitTestConnectionId(network, sessionToken)
        playersByConnection[connectionId] = playerId
        connectionsByPlayer[playerId] = connectionId
        session to connectionId
    }

    private fun nonAdjacentTerritoryId(
        mapDefinition: MapDefinition,
        fromTerritoryId: TerritoryId,
    ): TerritoryId =
        mapDefinition.territories
            .map { it.territoryId }
            .first { territoryId ->
                territoryId != fromTerritoryId &&
                    mapDefinition.territories
                        .first { it.territoryId == fromTerritoryId }
                        .edges
                        .none { edge -> edge.targetId == territoryId }
            }

    private fun defaultMapDefinition() =
        at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault()

    private fun isolatedFallbackMapDefinition() =
        MapDefinition(
            schemaVersion = 1,
            territories =
                listOf(
                    TerritoryDefinition(
                        territoryId = TerritoryId("alpha"),
                        edges = listOf(TerritoryEdgeDefinition(TerritoryId("beta"))),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("beta"),
                        edges = listOf(TerritoryEdgeDefinition(TerritoryId("alpha"))),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("gamma"),
                        edges = emptyList(),
                    ),
                ),
            continents =
                listOf(
                    ContinentDefinition(
                        continentId = at.aau.pulverfass.shared.ids.ContinentId("solo"),
                        territoryIds =
                            listOf(
                                TerritoryId("alpha"),
                                TerritoryId("beta"),
                                TerritoryId("gamma"),
                            ),
                        bonusValue = 1,
                    ),
                ),
        )

    private data class AttackFixture(
        val client: io.ktor.client.HttpClient,
        val network: ServerNetwork,
        val serverScope: CoroutineScope,
        val lobbyManager: LobbyManager,
        val routingService: MainServerLobbyRoutingService,
        val playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
        val connectionsByPlayer: ConcurrentHashMap<PlayerId, ConnectionId>,
    ) {
        suspend fun stop() {
            routingService.stop()
            lobbyManager.shutdownAll()
            serverScope.cancel()
        }
    }
}
