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

class PhaseTwoE2EIntegrationTest {
    @Test
    fun `normal attack without capture stays in attack phase`() =
        testApplication {
            val lobbyCode = LobbyCode("P2E1")
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
                    val actorSession = fixture.connectPlayer(fixture.client, attacker)
                    val watcherSession = fixture.connectPlayer(fixture.client, defender)

                    actorSession.first.send(
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
                                        requestId = "phase2-normal",
                                    ),
                                ),
                        ),
                    )

                    val expectedDelta =
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

                    assertEquals(expectedDelta, receiveRelevantTestPayload(actorSession.first))
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "phase2-normal"),
                        receiveRelevantTestPayload(actorSession.first),
                    )
                    assertEquals(expectedDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(actorSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(watcherSession.first))

                    val updatedState =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(TurnPhase.ATTACK, updatedState.activeTurnPhase)
                    assertEquals(1L, updatedState.stateVersion)

                    actorSession.first.close()
                    watcherSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `attack with capture applies moveAfterCapture and public capture data`() =
        testApplication {
            val lobbyCode = LobbyCode("P2E2")
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
                    val actorSession = fixture.connectPlayer(fixture.client, attacker)
                    val watcherSession = fixture.connectPlayer(fixture.client, defender)

                    actorSession.first.send(
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
                                        requestId = "phase2-capture",
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

                    assertEquals(captureDelta, receiveRelevantTestPayload(actorSession.first))
                    assertEquals(eliminationDelta, receiveRelevantTestPayload(actorSession.first))
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "phase2-capture"),
                        receiveRelevantTestPayload(actorSession.first),
                    )
                    assertEquals(captureDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertEquals(eliminationDelta, receiveRelevantTestPayload(watcherSession.first))

                    val updatedState =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(attacker, updatedState.ownerOf(toTerritoryId))
                    assertEquals(2, updatedState.troopCountOf(fromTerritoryId))
                    assertEquals(3, updatedState.troopCountOf(toTerritoryId))

                    actorSession.first.close()
                    watcherSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `elimination transfers cards and updates spectator and turn order`() =
        testApplication {
            val lobbyCode = LobbyCode("P2E3")
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
                    val actorSession = fixture.connectPlayer(fixture.client, attacker)
                    val defenderSession = fixture.connectPlayer(fixture.client, defender)

                    actorSession.first.send(
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
                                        requestId = "phase2-elimination",
                                    ),
                                ),
                        ),
                    )

                    receiveRelevantTestPayload(actorSession.first)
                    receiveRelevantTestPayload(actorSession.first)
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "phase2-elimination"),
                        receiveRelevantTestPayload(actorSession.first),
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
                        receiveRelevantTestPayload(actorSession.first),
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
                    assertEquals(listOf(attacker), updatedState.turnOrder)
                    assertEquals(true, updatedState.isSpectator(defender))
                    assertEquals(true, updatedState.tradeRequiredOnNextReinforcementPhaseFor(attacker))

                    actorSession.first.close()
                    defenderSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `no valid attacks auto ends phase to fortify`() =
        testApplication {
            val lobbyCode = LobbyCode("P2E4")
            val attacker = PlayerId(1)
            val defender = PlayerId(2)
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
                    mapDefinition = isolatedFallbackMapDefinition(),
                )
            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val actorSession = fixture.connectPlayer(fixture.client, attacker)
                    val watcherSession = fixture.connectPlayer(fixture.client, defender)

                    actorSession.first.send(
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
                                        requestId = "phase2-auto-end",
                                    ),
                                ),
                        ),
                    )

                    receiveRelevantTestPayload(actorSession.first)
                    assertEquals(
                        AttackResponse(lobbyCode = lobbyCode, requestId = "phase2-auto-end"),
                        receiveRelevantTestPayload(actorSession.first),
                    )
                    receiveRelevantTestPayload(actorSession.first)
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 2,
                            previousPhase = TurnPhase.ATTACK,
                            nextPhase = TurnPhase.FORTIFY,
                            activePlayerId = attacker,
                            turnCount = 1,
                        ),
                        receiveRelevantTestPayload(actorSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = attacker,
                            turnPhase = TurnPhase.FORTIFY,
                            turnCount = 1,
                            startPlayerId = attacker,
                        ),
                        receiveRelevantTestPayload(actorSession.first),
                    )

                    receiveRelevantTestPayload(watcherSession.first)
                    receiveRelevantTestPayload(watcherSession.first)
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

                    actorSession.first.close()
                    watcherSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `manual confirm attack done ends phase to fortify`() =
        testApplication {
            val lobbyCode = LobbyCode("P2E5")
            val attacker = PlayerId(1)
            val defender = PlayerId(2)
            val initialState =
                attackGame(
                    lobbyCode = lobbyCode,
                    players = listOf(attacker, defender),
                    activePlayerId = attacker,
                    turnPhase = TurnPhase.ATTACK,
                    rngSeed = 1L,
                    rngState = 16L,
                    owners = emptyMap(),
                    troopCounts = emptyMap(),
                )
            val fixture = createFixture(lobbyCode, initialState, this)

            try {
                coroutineScope {
                    val actorSession = fixture.connectPlayer(fixture.client, attacker)
                    val watcherSession = fixture.connectPlayer(fixture.client, defender)

                    actorSession.first.send(
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
                            fromVersion = 1,
                            toVersion = 1,
                            events = listOf(fortifyUpdate),
                        )

                    assertEquals(confirmDelta, receiveRelevantTestPayload(actorSession.first))
                    assertEquals(
                        ConfirmAttackDoneResponse(lobbyCode),
                        receiveRelevantTestPayload(actorSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.ATTACK,
                            nextPhase = TurnPhase.FORTIFY,
                            activePlayerId = attacker,
                            turnCount = 1,
                        ),
                        receiveRelevantTestPayload(actorSession.first),
                    )
                    assertEquals(fortifyUpdate, receiveRelevantTestPayload(actorSession.first))

                    assertEquals(confirmDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.ATTACK,
                            nextPhase = TurnPhase.FORTIFY,
                            activePlayerId = attacker,
                            turnCount = 1,
                        ),
                        receiveRelevantTestPayload(watcherSession.first),
                    )
                    assertEquals(fortifyUpdate, receiveRelevantTestPayload(watcherSession.first))

                    val updatedState =
                        fixture.lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(TurnPhase.FORTIFY, updatedState.activeTurnPhase)
                    assertEquals(1L, updatedState.stateVersion)

                    actorSession.first.close()
                    watcherSession.first.close()
                }
            } finally {
                fixture.stop()
            }
        }

    private fun createFixture(
        lobbyCode: LobbyCode,
        initialState: GameState,
        builder: ApplicationTestBuilder,
    ): PhaseTwoFixture {
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

        return PhaseTwoFixture(
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

    private suspend fun PhaseTwoFixture.connectPlayer(
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

    private data class PhaseTwoFixture(
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
