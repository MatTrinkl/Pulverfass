package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class TurnAdvanceIntegrationTest {
    @Test
    fun `active player can advance and lobby receives exactly one turn state update`() =
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

            val lobbyCode = LobbyCode("TA01")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val playerThree = PlayerId(3)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                    ),
            )
            lobbyManager.createLobby(
                lobbyCode = LobbyCode("TA99"),
                initialState =
                    runningTurnStateGame(
                        lobbyCode = LobbyCode("TA99"),
                        players = listOf(playerThree),
                        activePlayerId = playerThree,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                    ),
            )
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
                    val outsiderSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerThree,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        expectedPhase = TurnPhase.REINFORCEMENTS,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        TurnAdvanceResponse(lobbyCode),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.REINFORCEMENTS,
                            nextPhase = TurnPhase.ATTACK,
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.REINFORCEMENTS,
                            nextPhase = TurnPhase.ATTACK,
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receivePayload(playerTwoSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                        receivePayload(playerTwoSession.first),
                    )
                    assertNull(receivePayloadOrNull(outsiderSession.first))
                    assertNull(receivePayloadOrNull(playerOneSession.first))
                    assertEquals(
                        TurnPhase.ATTACK,
                        lobbyManager.getLobby(lobbyCode)?.currentState()?.activeTurnPhase,
                    )

                    playerOneSession.first.close()
                    playerTwoSession.first.close()
                    outsiderSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `phase advance sends delta then boundary then turn update in deterministic order`() =
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

            val lobbyCode = LobbyCode("TA08")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                    ),
            )
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
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        expectedPhase = TurnPhase.REINFORCEMENTS,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    TurnStateUpdatedEvent(
                                        lobbyCode = lobbyCode,
                                        activePlayerId = playerOne,
                                        turnPhase = TurnPhase.ATTACK,
                                        turnCount = 1,
                                        startPlayerId = playerOne,
                                    ),
                                ),
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
                            stateVersion = 1,
                            previousPhase = TurnPhase.REINFORCEMENTS,
                            nextPhase = TurnPhase.ATTACK,
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )

                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    TurnStateUpdatedEvent(
                                        lobbyCode = lobbyCode,
                                        activePlayerId = playerOne,
                                        turnPhase = TurnPhase.ATTACK,
                                        turnCount = 1,
                                        startPlayerId = playerOne,
                                    ),
                                ),
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.REINFORCEMENTS,
                            nextPhase = TurnPhase.ATTACK,
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )

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
    fun `turn change broadcasts full public snapshot after turn state update`() =
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

            val lobbyCode = LobbyCode("TA09")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
                    ),
            )
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
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        expectedPhase = TurnPhase.DRAW_CARD,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    TurnStateUpdatedEvent(
                                        lobbyCode = lobbyCode,
                                        activePlayerId = playerTwo,
                                        turnPhase = TurnPhase.REINFORCEMENTS,
                                        turnCount = 1,
                                        startPlayerId = playerOne,
                                    ),
                                ),
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.DRAW_CARD,
                            nextPhase = TurnPhase.REINFORCEMENTS,
                            activePlayerId = playerTwo,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )

                    val snapshot =
                        assertIs<GameStateSnapshotBroadcast>(
                            receiveAnyPayload(playerTwoSession.first),
                        )
                    assertEquals(lobbyCode, snapshot.lobbyCode)
                    assertEquals(1, snapshot.stateVersion)
                    assertEquals(defaultMapDefinition().mapHash, snapshot.determinism.mapHash)
                    assertEquals(
                        defaultMapDefinition().schemaVersion,
                        snapshot.determinism.schemaVersion,
                    )
                    assertEquals(playerTwo, snapshot.turnState.activePlayerId)
                    assertEquals(TurnPhase.REINFORCEMENTS, snapshot.turnState.turnPhase)
                    assertEquals(1, snapshot.turnState.turnCount)
                    assertEquals(playerOne, snapshot.turnState.startPlayerId)
                    assertEquals(23, snapshot.territoryStates.size)
                    assertEquals(23, snapshot.definition.territories.size)

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
    fun `non active player gets not active player error and no state change`() =
        testApplication {
            val result =
                exerciseFailingAdvance(
                    lobbyCode = LobbyCode("TA02"),
                    state =
                        runningTurnStateGame(
                            lobbyCode = LobbyCode("TA02"),
                            players = listOf(PlayerId(1), PlayerId(2)),
                            activePlayerId = PlayerId(1),
                            turnPhase = TurnPhase.ATTACK,
                        ),
                    requesterPlayerId = PlayerId(2),
                    request =
                        TurnAdvanceRequest(
                            lobbyCode = LobbyCode("TA02"),
                            playerId = PlayerId(2),
                            expectedPhase = TurnPhase.ATTACK,
                        ),
                )

            assertEquals(TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER, result.first.code)
            assertEquals(TurnPhase.ATTACK, result.second.activeTurnPhase)
        }

    @Test
    fun `paused game gets game paused error and no state change`() =
        testApplication {
            val result =
                exerciseFailingAdvance(
                    lobbyCode = LobbyCode("TA03"),
                    state =
                        runningTurnStateGame(
                            lobbyCode = LobbyCode("TA03"),
                            players = listOf(PlayerId(1), PlayerId(2)),
                            activePlayerId = PlayerId(1),
                            turnPhase = TurnPhase.FORTIFY,
                            isPaused = true,
                            pauseReason = "manual-pause",
                        ),
                    requesterPlayerId = PlayerId(1),
                    request =
                        TurnAdvanceRequest(
                            lobbyCode = LobbyCode("TA03"),
                            playerId = PlayerId(1),
                            expectedPhase = TurnPhase.FORTIFY,
                        ),
                )

            assertEquals(TurnAdvanceErrorCode.GAME_PAUSED, result.first.code)
            assertEquals(TurnPhase.FORTIFY, result.second.activeTurnPhase)
            assertEquals(true, result.second.turnState?.isPaused)
        }

    @Test
    fun `phase mismatch gets phase mismatch error and no state change`() =
        testApplication {
            val result =
                exerciseFailingAdvance(
                    lobbyCode = LobbyCode("TA04"),
                    state =
                        runningTurnStateGame(
                            lobbyCode = LobbyCode("TA04"),
                            players = listOf(PlayerId(1), PlayerId(2)),
                            activePlayerId = PlayerId(1),
                            turnPhase = TurnPhase.DRAW_CARD,
                        ),
                    requesterPlayerId = PlayerId(1),
                    request =
                        TurnAdvanceRequest(
                            lobbyCode = LobbyCode("TA04"),
                            playerId = PlayerId(1),
                            expectedPhase = TurnPhase.ATTACK,
                        ),
                )

            assertEquals(TurnAdvanceErrorCode.PHASE_MISMATCH, result.first.code)
            assertEquals(TurnPhase.DRAW_CARD, result.second.activeTurnPhase)
        }

    @Test
    fun `disconnecting a non active player does not pause the game`() =
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

            val lobbyCode = LobbyCode("TA05")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.ATTACK,
                    ),
            )
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

                    disconnectPlayer(
                        playerId = playerTwo,
                        session = playerTwoSession.first,
                        connectionId = playerTwoSession.second,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                        routingService = routingService,
                    )

                    assertNull(receivePayloadOrNull(playerOneSession.first))
                    val snapshot =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("snapshot missing")
                    assertEquals(playerOne, snapshot.activePlayer)
                    assertEquals(false, snapshot.turnState?.isPaused)

                    playerOneSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `advance pauses when next active player is disconnected`() =
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

            val lobbyCode = LobbyCode("TA06")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
                    ),
            )
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

                    disconnectPlayer(
                        playerId = playerTwo,
                        session = playerTwoSession.first,
                        connectionId = playerTwoSession.second,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                        routingService = routingService,
                    )

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        expectedPhase = TurnPhase.DRAW_CARD,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        TurnAdvanceResponse(lobbyCode),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.DRAW_CARD,
                            nextPhase = TurnPhase.REINFORCEMENTS,
                            activePlayerId = playerTwo,
                            turnCount = 1,
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = playerOne,
                            isPaused = true,
                            pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                            pausedPlayerId = playerTwo,
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    val snapshot =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("snapshot missing")
                    assertEquals(playerTwo, snapshot.activePlayer)
                    assertEquals(true, snapshot.turnState?.isPaused)
                    assertEquals(
                        TurnPauseReasons.WAITING_FOR_PLAYER,
                        snapshot.turnState?.pauseReason,
                    )
                    assertEquals(playerTwo, snapshot.turnState?.pausedPlayerId)

                    playerOneSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `reconnect resumes paused turn and keeps active player unchanged`() =
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

            val lobbyCode = LobbyCode("TA07")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerTwo,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        isPaused = true,
                        pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                        pausedPlayerId = playerTwo,
                    ),
            )
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

                    routingService.onPlayerConnected(playerTwo)

                    val expectedEvent =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = playerOne,
                            isPaused = false,
                            pauseReason = null,
                            pausedPlayerId = null,
                        )
                    assertEquals(expectedEvent, receivePayload(playerOneSession.first))
                    assertEquals(expectedEvent, receivePayload(playerTwoSession.first))

                    val snapshot =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("snapshot missing")
                    assertEquals(playerTwo, snapshot.activePlayer)
                    assertEquals(false, snapshot.turnState?.isPaused)
                    assertEquals(null, snapshot.turnState?.pausedPlayerId)

                    playerOneSession.first.close()
                    playerTwoSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private suspend fun ApplicationTestBuilder.exerciseFailingAdvance(
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: TurnAdvanceRequest,
    ): Pair<TurnAdvanceErrorResponse, GameState> {
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
                    assertIs<TurnAdvanceErrorResponse>(receivePayload(requesterSession.first))
                assertNull(receivePayloadOrNull(requesterSession.first))

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

    private fun runningTurnStateGame(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        turnPhase: TurnPhase,
        isPaused: Boolean = false,
        pauseReason: String? = null,
        pausedPlayerId: PlayerId? = null,
    ): GameState =
        GameState
            .initial(
                lobbyCode = lobbyCode,
                mapDefinition = defaultMapDefinition(),
                players = players,
                playerDisplayNames = players.associateWith { "Player ${it.value}" },
            ).copy(
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
                        isPaused = isPaused,
                        pauseReason = pauseReason,
                        pausedPlayerId = pausedPlayerId,
                    ),
                status = GameStatus.RUNNING,
            )

    private suspend fun disconnectPlayer(
        playerId: PlayerId,
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
        connectionId: ConnectionId,
        playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
        connectionsByPlayer: ConcurrentHashMap<PlayerId, ConnectionId>,
        routingService: MainServerLobbyRoutingService,
    ) {
        playersByConnection.remove(connectionId)
        connectionsByPlayer.remove(playerId)
        routingService.onPlayerDisconnected(playerId)
        session.close()
    }

    private suspend fun connectSessionWithConnection(
        client: io.ktor.client.HttpClient,
        network: ServerNetwork,
        playerId: PlayerId,
        playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
        connectionsByPlayer: ConcurrentHashMap<PlayerId, ConnectionId>,
    ) = coroutineScope {
        val connectedDeferred =
            async {
                withTimeout(5_000) {
                    network.events
                        .filterIsInstance<Network.Event.Connected<ConnectionId>>()
                        .first()
                }
            }

        val session = client.webSocketSession("/ws")
        val connected = connectedDeferred.await()
        playersByConnection[connected.connectionId] = playerId
        connectionsByPlayer[playerId] = connected.connectionId
        session to connected.connectionId
    }

    private suspend fun receivePayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any {
        repeat(10) {
            val payload = receiveAnyPayload(session)
            if (payload !is GameStateDeltaEvent && payload !is GameStateSnapshotBroadcast) {
                return payload
            }
        }
        throw AssertionError("Expected non-delta payload within 10 messages.")
    }

    private suspend fun receiveAnyPayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any {
        val frame = withTimeout(5_000) { session.incoming.receive() }
        assertTrue(frame is Frame.Binary)
        return MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
    }

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any? {
        repeat(5) {
            val frame =
                withTimeoutOrNull(200) {
                    session.incoming.receive()
                } ?: return null
            assertTrue(frame is Frame.Binary)
            val payload = MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
            if (payload !is GameStateDeltaEvent && payload !is GameStateSnapshotBroadcast) {
                return payload
            }
        }
        return null
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(
            value is T,
            "Expected ${T::class.simpleName}, but was ${value?.let { it::class.simpleName }}.",
        )
        return value as T
    }

    private fun defaultMapDefinition() =
        at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault()
}
