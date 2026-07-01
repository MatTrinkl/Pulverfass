package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.request.FortifyMoveRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.response.FortifyMoveResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorResponse
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class FortifyMoveIntegrationTest {
    @Test
    fun `active player can fortify and finish phase automatically afterwards`() =
        testApplication {
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

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("1143")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    fortifyReadyGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        territoryOwners =
                            mapOf(
                                TerritoryId("alpha") to playerOne,
                                TerritoryId("beta") to playerOne,
                                TerritoryId("gamma") to playerOne,
                            ),
                        troopCounts =
                            mapOf(
                                TerritoryId("alpha") to 4,
                                TerritoryId("beta") to 2,
                                TerritoryId("gamma") to 1,
                            ),
                    ),
            )
            var fortifyMoveAppliedSawUsedFlag: Boolean? = null
            lobbyManager.registerAcceptedEventListener { _, event, _, _ ->
                if (event is FortifyMoveAppliedEvent) {
                    fortifyMoveAppliedSawUsedFlag =
                        lobbyManager.getLobby(lobbyCode)?.currentState()?.fortifyUsedThisTurn
                }
            }
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val playerOneSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val playerTwoSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    FortifyMoveRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        fromTerritoryId = TerritoryId("alpha"),
                                        toTerritoryId = TerritoryId("gamma"),
                                        troopCount = 2,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 7,
                            toVersion = 7,
                            events =
                                listOf(
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = TerritoryId("alpha"),
                                        troopCount = 2,
                                        stateVersion = 7,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = TerritoryId("gamma"),
                                        troopCount = 3,
                                        stateVersion = 7,
                                    ),
                                ),
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        FortifyMoveResponse(lobbyCode),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 7,
                            toVersion = 7,
                            events =
                                listOf(
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = TerritoryId("alpha"),
                                        troopCount = 2,
                                        stateVersion = 7,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = TerritoryId("gamma"),
                                        troopCount = 3,
                                        stateVersion = 7,
                                    ),
                                ),
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )

                    val fortifiedState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("snapshot missing")
                    assertEquals(TurnPhase.FORTIFY, fortifiedState.activeTurnPhase)
                    assertEquals(2, fortifiedState.troopCountOf(TerritoryId("alpha")))
                    assertEquals(3, fortifiedState.troopCountOf(TerritoryId("gamma")))
                    assertTrue(fortifiedState.fortifyUsedThisTurn)
                    assertEquals(true, fortifyMoveAppliedSawUsedFlag)
                    assertEquals(null, receivePayloadOrNull(playerOneSession.first))
                    assertEquals(null, receivePayloadOrNull(playerTwoSession.first))

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        expectedPhase = TurnPhase.FORTIFY,
                                    ),
                                ),
                        ),
                    )

                    val drawCardUpdate =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.DRAW_CARD,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        )
                    val nextReinforcementsUpdate =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        )
                    val nextReinforcementsGrant =
                        ReinforcementsGrantedEvent(
                            lobbyCode = lobbyCode,
                            playerId = playerTwo,
                            amount = 3,
                            territoryBonus = 3,
                            continentBonus = 0,
                            cardBonus = 0,
                        )

                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 9,
                            toVersion = 9,
                            events = listOf(drawCardUpdate),
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 10,
                            toVersion = 10,
                            events = listOf(nextReinforcementsUpdate),
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 11,
                            toVersion = 11,
                            events = listOf(nextReinforcementsGrant),
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        TurnAdvanceResponse(lobbyCode),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 11,
                            previousPhase = TurnPhase.FORTIFY,
                            nextPhase = TurnPhase.REINFORCEMENTS,
                            activePlayerId = playerTwo,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        nextReinforcementsUpdate,
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertIs<GameStateSnapshotBroadcast>(receiveAnyPayload(playerOneSession.first))
                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 9,
                            toVersion = 9,
                            events = listOf(drawCardUpdate),
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 10,
                            toVersion = 10,
                            events = listOf(nextReinforcementsUpdate),
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 11,
                            toVersion = 11,
                            events = listOf(nextReinforcementsGrant),
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 11,
                            previousPhase = TurnPhase.FORTIFY,
                            nextPhase = TurnPhase.REINFORCEMENTS,
                            activePlayerId = playerTwo,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        nextReinforcementsUpdate,
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertIs<GameStateSnapshotBroadcast>(receiveAnyPayload(playerTwoSession.first))

                    val advancedState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("snapshot missing")
                    assertEquals(TurnPhase.REINFORCEMENTS, advancedState.activeTurnPhase)
                    assertEquals(playerTwo, advancedState.activePlayer)
                    assertEquals(3, advancedState.pendingReinforcementsFor(playerTwo))

                    playerOneSession.first.close()
                    playerTwoSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `fortify rejects paused game without changing troops`() =
        testApplication {
            val playerOne = PlayerId(1)
            val state =
                fortifyReadyGame(
                    lobbyCode = LobbyCode("1144"),
                    players = listOf(playerOne, PlayerId(2)),
                    activePlayerId = playerOne,
                    territoryOwners =
                        mapOf(
                            TerritoryId("alpha") to playerOne,
                            TerritoryId("beta") to playerOne,
                            TerritoryId("gamma") to playerOne,
                        ),
                    troopCounts =
                        mapOf(
                            TerritoryId("alpha") to 4,
                            TerritoryId("beta") to 2,
                            TerritoryId("gamma") to 1,
                        ),
                    isPaused = true,
                )

            val (error, snapshot) =
                exerciseFailingFortify(
                    lobbyCode = LobbyCode("1144"),
                    state = state,
                    requesterPlayerId = playerOne,
                    request =
                        FortifyMoveRequest(
                            lobbyCode = LobbyCode("1144"),
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("alpha"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )

            assertEquals(
                FortifyMoveErrorResponse(
                    code = FortifyMoveErrorCode.GAME_PAUSED,
                    reason = "Lobby '1144' ist pausiert; Fortify ist aktuell nicht erlaubt.",
                ),
                error,
            )
            assertEquals(4, snapshot.troopCountOf(TerritoryId("alpha")))
            assertEquals(1, snapshot.troopCountOf(TerritoryId("gamma")))
            assertEquals(TurnPhase.FORTIFY, snapshot.activeTurnPhase)
        }

    @Test
    fun `fortify rejects requester mismatch before state mutation`() =
        testApplication {
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val state =
                fortifyReadyGame(
                    lobbyCode = LobbyCode("1145"),
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    territoryOwners =
                        mapOf(
                            TerritoryId("alpha") to playerOne,
                            TerritoryId("beta") to playerOne,
                            TerritoryId("gamma") to playerOne,
                        ),
                    troopCounts =
                        mapOf(
                            TerritoryId("alpha") to 4,
                            TerritoryId("beta") to 2,
                            TerritoryId("gamma") to 1,
                        ),
                )

            val (error, snapshot) =
                exerciseFailingFortify(
                    lobbyCode = LobbyCode("1145"),
                    state = state,
                    requesterPlayerId = playerTwo,
                    request =
                        FortifyMoveRequest(
                            lobbyCode = LobbyCode("1145"),
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("alpha"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )

            assertEquals(FortifyMoveErrorCode.REQUESTER_MISMATCH, error.code)
            assertEquals(4, snapshot.troopCountOf(TerritoryId("alpha")))
            assertEquals(1, snapshot.troopCountOf(TerritoryId("gamma")))
            assertEquals(false, snapshot.fortifyUsedThisTurn)
        }

    @Test
    fun `fortify rejects owned territories without connected path`() =
        testApplication {
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val state =
                fortifyReadyGame(
                    lobbyCode = LobbyCode("1146"),
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    territoryOwners =
                        mapOf(
                            TerritoryId("alpha") to playerOne,
                            TerritoryId("beta") to playerTwo,
                            TerritoryId("gamma") to playerOne,
                        ),
                    troopCounts =
                        mapOf(
                            TerritoryId("alpha") to 4,
                            TerritoryId("beta") to 2,
                            TerritoryId("gamma") to 1,
                        ),
                )

            val (error, snapshot) =
                exerciseFailingFortify(
                    lobbyCode = LobbyCode("1146"),
                    state = state,
                    requesterPlayerId = playerOne,
                    request =
                        FortifyMoveRequest(
                            lobbyCode = LobbyCode("1146"),
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("alpha"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )

            assertEquals(
                FortifyMoveErrorCode.NO_PATH,
                error.code,
            )
            assertEquals(4, snapshot.troopCountOf(TerritoryId("alpha")))
            assertEquals(1, snapshot.troopCountOf(TerritoryId("gamma")))
            assertEquals(false, snapshot.fortifyUsedThisTurn)
        }

    @Test
    fun `fortify rejects missing lobby`() =
        testApplication {
            val playerOne = PlayerId(1)

            val error =
                exerciseMissingLobbyFortify(
                    requesterPlayerId = playerOne,
                    request =
                        FortifyMoveRequest(
                            lobbyCode = LobbyCode("1147"),
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("alpha"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )

            assertEquals(
                FortifyMoveErrorResponse(
                    code = FortifyMoveErrorCode.GAME_NOT_FOUND,
                    reason = "Lobby '1147' wurde nicht gefunden.",
                ),
                error,
            )
        }

    @Test
    fun `fortify rejects player who is not active`() =
        testApplication {
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val state =
                fortifyReadyGame(
                    lobbyCode = LobbyCode("1148"),
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    territoryOwners =
                        mapOf(
                            TerritoryId("alpha") to playerTwo,
                            TerritoryId("beta") to playerTwo,
                            TerritoryId("gamma") to playerTwo,
                        ),
                    troopCounts =
                        mapOf(
                            TerritoryId("alpha") to 4,
                            TerritoryId("beta") to 2,
                            TerritoryId("gamma") to 1,
                        ),
                )

            val (error, snapshot) =
                exerciseFailingFortify(
                    lobbyCode = LobbyCode("1148"),
                    state = state,
                    requesterPlayerId = playerTwo,
                    request =
                        FortifyMoveRequest(
                            lobbyCode = LobbyCode("1148"),
                            playerId = playerTwo,
                            fromTerritoryId = TerritoryId("alpha"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )

            assertEquals(FortifyMoveErrorCode.NOT_ACTIVE_PLAYER, error.code)
            assertEquals(4, snapshot.troopCountOf(TerritoryId("alpha")))
            assertEquals(1, snapshot.troopCountOf(TerritoryId("gamma")))
            assertEquals(false, snapshot.fortifyUsedThisTurn)
        }

    @Test
    fun `fortify rejects wrong phase`() =
        testApplication {
            val playerOne = PlayerId(1)
            val fortifyState =
                fortifyReadyGame(
                    lobbyCode = LobbyCode("1149"),
                    players = listOf(playerOne, PlayerId(2)),
                    activePlayerId = playerOne,
                    territoryOwners =
                        mapOf(
                            TerritoryId("alpha") to playerOne,
                            TerritoryId("beta") to playerOne,
                            TerritoryId("gamma") to playerOne,
                        ),
                    troopCounts =
                        mapOf(
                            TerritoryId("alpha") to 4,
                            TerritoryId("beta") to 2,
                            TerritoryId("gamma") to 1,
                        ),
                )
            val attackState =
                fortifyState.copy(
                    turnState = fortifyState.turnState?.copy(turnPhase = TurnPhase.ATTACK),
                )

            val (error, snapshot) =
                exerciseFailingFortify(
                    lobbyCode = LobbyCode("1149"),
                    state = attackState,
                    requesterPlayerId = playerOne,
                    request =
                        FortifyMoveRequest(
                            lobbyCode = LobbyCode("1149"),
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("alpha"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )

            assertEquals(FortifyMoveErrorCode.WRONG_PHASE, error.code)
            assertEquals(TurnPhase.ATTACK, snapshot.activeTurnPhase)
            assertEquals(false, snapshot.fortifyUsedThisTurn)
        }

    @Test
    fun `fortify rejects unowned destination`() =
        testApplication {
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val state =
                fortifyReadyGame(
                    lobbyCode = LobbyCode("1150"),
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    territoryOwners =
                        mapOf(
                            TerritoryId("alpha") to playerOne,
                            TerritoryId("beta") to playerOne,
                            TerritoryId("gamma") to playerTwo,
                        ),
                    troopCounts =
                        mapOf(
                            TerritoryId("alpha") to 4,
                            TerritoryId("beta") to 2,
                            TerritoryId("gamma") to 1,
                        ),
                )

            val (error, snapshot) =
                exerciseFailingFortify(
                    lobbyCode = LobbyCode("1150"),
                    state = state,
                    requesterPlayerId = playerOne,
                    request =
                        FortifyMoveRequest(
                            lobbyCode = LobbyCode("1150"),
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("alpha"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )

            assertEquals(FortifyMoveErrorCode.TERRITORY_NOT_OWNED, error.code)
            assertEquals(4, snapshot.troopCountOf(TerritoryId("alpha")))
            assertEquals(1, snapshot.troopCountOf(TerritoryId("gamma")))
            assertEquals(false, snapshot.fortifyUsedThisTurn)
        }

    @Test
    fun `fortify rejects insufficient source troops`() =
        testApplication {
            val playerOne = PlayerId(1)
            val state =
                fortifyReadyGame(
                    lobbyCode = LobbyCode("1151"),
                    players = listOf(playerOne, PlayerId(2)),
                    activePlayerId = playerOne,
                    territoryOwners =
                        mapOf(
                            TerritoryId("alpha") to playerOne,
                            TerritoryId("beta") to playerOne,
                            TerritoryId("gamma") to playerOne,
                        ),
                    troopCounts =
                        mapOf(
                            TerritoryId("alpha") to 1,
                            TerritoryId("beta") to 2,
                            TerritoryId("gamma") to 1,
                        ),
                )

            val (error, snapshot) =
                exerciseFailingFortify(
                    lobbyCode = LobbyCode("1151"),
                    state = state,
                    requesterPlayerId = playerOne,
                    request =
                        FortifyMoveRequest(
                            lobbyCode = LobbyCode("1151"),
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("alpha"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )

            assertEquals(FortifyMoveErrorCode.INSUFFICIENT_TROOPS, error.code)
            assertEquals(1, snapshot.troopCountOf(TerritoryId("alpha")))
            assertEquals(1, snapshot.troopCountOf(TerritoryId("gamma")))
            assertEquals(false, snapshot.fortifyUsedThisTurn)
        }

    @Test
    fun `fortify rejects second move in same turn`() =
        testApplication {
            val playerOne = PlayerId(1)
            val state =
                fortifyReadyGame(
                    lobbyCode = LobbyCode("1156"),
                    players = listOf(playerOne, PlayerId(2)),
                    activePlayerId = playerOne,
                    territoryOwners =
                        mapOf(
                            TerritoryId("alpha") to playerOne,
                            TerritoryId("beta") to playerOne,
                            TerritoryId("gamma") to playerOne,
                        ),
                    troopCounts =
                        mapOf(
                            TerritoryId("alpha") to 4,
                            TerritoryId("beta") to 2,
                            TerritoryId("gamma") to 1,
                        ),
                    fortifyUsedThisTurn = true,
                )

            val (error, snapshot) =
                exerciseFailingFortify(
                    lobbyCode = LobbyCode("1156"),
                    state = state,
                    requesterPlayerId = playerOne,
                    request =
                        FortifyMoveRequest(
                            lobbyCode = LobbyCode("1156"),
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("alpha"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )

            assertEquals(FortifyMoveErrorCode.FORTIFY_ALREADY_USED, error.code)
            assertEquals(4, snapshot.troopCountOf(TerritoryId("alpha")))
            assertEquals(1, snapshot.troopCountOf(TerritoryId("gamma")))
            assertEquals(true, snapshot.fortifyUsedThisTurn)
        }

    private suspend fun ApplicationTestBuilder.exerciseMissingLobbyFortify(
        requesterPlayerId: PlayerId,
        request: FortifyMoveRequest,
    ): FortifyMoveErrorResponse {
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

        application {
            module(network)
        }
        routingService.start(serverScope)

        val client =
            createClient {
                install(WebSockets)
            }

        return try {
            coroutineScope {
                val requesterSession =
                    connectSessionWithConnection(
                        client = client,
                        network = network,
                        playerId = requesterPlayerId,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                    )

                requesterSession.first.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(request),
                    ),
                )

                val error =
                    assertIs<FortifyMoveErrorResponse>(receivePayload(requesterSession.first))
                requesterSession.first.close()
                error
            }
        } finally {
            routingService.stop()
            lobbyManager.shutdownAll()
            serverScope.cancel()
        }
    }

    private suspend fun ApplicationTestBuilder.exerciseFailingFortify(
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: FortifyMoveRequest,
    ): Pair<FortifyMoveErrorResponse, GameState> {
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

        application {
            module(network)
        }
        lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = state)
        routingService.start(serverScope)

        val client =
            createClient {
                install(WebSockets)
            }

        return try {
            coroutineScope {
                val requesterSession =
                    connectSessionWithConnection(
                        client = client,
                        network = network,
                        playerId = requesterPlayerId,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                    )

                requesterSession.first.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(request),
                    ),
                )

                val error =
                    assertIs<FortifyMoveErrorResponse>(receivePayload(requesterSession.first))
                val snapshot =
                    lobbyManager.getLobby(lobbyCode)?.currentState()
                        ?: error("snapshot missing")
                requesterSession.first.close()
                error to snapshot
            }
        } finally {
            routingService.stop()
            lobbyManager.shutdownAll()
            serverScope.cancel()
        }
    }

    private fun fortifyReadyGame(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        territoryOwners: Map<TerritoryId, PlayerId>,
        troopCounts: Map<TerritoryId, Int>,
        fortifyUsedThisTurn: Boolean = false,
        isPaused: Boolean = false,
    ): GameState {
        val reducer = DefaultLobbyEventReducer()
        val initializedState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleFortifyMapDefinition(),
                players = players,
                playerDisplayNames = players.associateWith { "Player ${it.value}" },
            )
        val ownedState =
            territoryOwners.entries.fold(initializedState) { state, (territoryId, ownerId) ->
                reducer.apply(
                    state,
                    TerritoryOwnerChangedEvent(lobbyCode, territoryId, ownerId),
                )
            }
        val occupiedState =
            troopCounts.entries.fold(ownedState) { state, (territoryId, troopCount) ->
                reducer.apply(
                    state,
                    TerritoryTroopsChangedEvent(lobbyCode, territoryId, troopCount),
                )
            }

        return occupiedState.copy(
            lobbyOwner = players.firstOrNull(),
            activePlayer = activePlayerId,
            turnOrder = players,
            turnNumber = 1,
            turnState =
                TurnState(
                    activePlayerId = activePlayerId,
                    turnPhase = TurnPhase.FORTIFY,
                    turnCount = 1,
                    startPlayerId = players.first(),
                    isPaused = isPaused,
                    pauseReason =
                        if (isPaused) {
                            TurnPauseReasons.WAITING_FOR_PLAYER
                        } else {
                            null
                        },
                    pausedPlayerId = if (isPaused) activePlayerId else null,
                ),
            gameStarted = true,
            status = GameStatus.RUNNING,
            fortifyUsedThisTurn = fortifyUsedThisTurn,
        )
    }

    private fun sampleFortifyMapDefinition(): MapDefinition =
        MapDefinition(
            schemaVersion = 1,
            territories =
                listOf(
                    TerritoryDefinition(
                        territoryId = TerritoryId("alpha"),
                        edges = listOf(TerritoryEdgeDefinition(targetId = TerritoryId("beta"))),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("beta"),
                        edges =
                            listOf(
                                TerritoryEdgeDefinition(targetId = TerritoryId("alpha")),
                                TerritoryEdgeDefinition(targetId = TerritoryId("gamma")),
                            ),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("gamma"),
                        edges = listOf(TerritoryEdgeDefinition(targetId = TerritoryId("beta"))),
                    ),
                ),
            continents =
                listOf(
                    ContinentDefinition(
                        continentId = ContinentId("center"),
                        territoryIds =
                            listOf(
                                TerritoryId("alpha"),
                                TerritoryId("beta"),
                                TerritoryId("gamma"),
                            ),
                        bonusValue = 2,
                    ),
                ),
        )

    private suspend fun connectSessionWithConnection(
        client: io.ktor.client.HttpClient,
        network: ServerNetwork,
        playerId: PlayerId,
        playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
        connectionsByPlayer: ConcurrentHashMap<PlayerId, ConnectionId>,
    ) = coroutineScope {
        val session = client.webSocketSession("/ws")
        val sessionToken = receiveTestConnectionToken(session)
        val connectionId = awaitTestConnectionId(network, sessionToken)
        playersByConnection[connectionId] = playerId
        connectionsByPlayer[playerId] = connectionId
        session to connectionId
    }

    private suspend fun receivePayload(session: DefaultClientWebSocketSession): Any =
        receiveRelevantTestPayload(session = session, skipGameSync = true)

    private suspend fun receiveAnyPayload(session: DefaultClientWebSocketSession): Any =
        receiveRelevantTestPayload(session)

    private suspend fun receivePayloadOrNull(session: DefaultClientWebSocketSession): Any? =
        receiveRelevantTestPayloadOrNull(
            session = session,
            skipGameSync = true,
            timeoutMillis = 200,
            maxMessages = 5,
        )

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(
            value is T,
            "Expected ${T::class.simpleName}, but was ${value?.let { it::class.simpleName }}.",
        )
        return value as T
    }
}
