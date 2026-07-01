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
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.request.ConfirmAttackDoneRequest
import at.aau.pulverfass.shared.message.lobby.response.ConfirmAttackDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorResponse
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.HttpClient
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

class ConfirmAttackDoneIntegrationTest {
    @Test
    fun `manual confirm advances attack phase to fortify`() =
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

            val lobbyCode = LobbyCode("1050")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    attackState(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                    ),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val actorSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val watcherSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    actorSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ConfirmAttackDoneRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                    ),
                                ),
                        ),
                    )

                    val expectedUpdate =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.FORTIFY,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        )
                    val expectedDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events = listOf(expectedUpdate),
                        )

                    assertEquals(expectedDelta, receiveRelevantTestPayload(actorSession.first))
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
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receiveRelevantTestPayload(actorSession.first),
                    )
                    assertEquals(expectedUpdate, receiveRelevantTestPayload(actorSession.first))

                    assertEquals(expectedDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.ATTACK,
                            nextPhase = TurnPhase.FORTIFY,
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receiveRelevantTestPayload(watcherSession.first),
                    )
                    assertEquals(expectedUpdate, receiveRelevantTestPayload(watcherSession.first))

                    val updatedState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(TurnPhase.FORTIFY, updatedState.activeTurnPhase)
                    assertNull(receiveRelevantTestPayloadOrNull(actorSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(watcherSession.first))

                    actorSession.first.close()
                    watcherSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `confirm attack done is rejected when requester is not active player`() =
        testApplication {
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val (error, snapshot) =
                exerciseFailingConfirm(
                    builder = this,
                    lobbyCode = LobbyCode("1051"),
                    state =
                        attackState(
                            lobbyCode = LobbyCode("1051"),
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerTwo,
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        ConfirmAttackDoneRequest(
                            lobbyCode = LobbyCode("1051"),
                            playerId = playerOne,
                        ),
                )

            assertEquals(ConfirmAttackDoneErrorCode.NOT_ACTIVE_PLAYER, error.code)
            assertEquals(TurnPhase.ATTACK, snapshot.activeTurnPhase)
        }

    @Test
    fun `confirm attack done is rejected when phase is not attack`() =
        testApplication {
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val (error, snapshot) =
                exerciseFailingConfirm(
                    builder = this,
                    lobbyCode = LobbyCode("1052"),
                    state =
                        attackState(
                            lobbyCode = LobbyCode("1052"),
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.FORTIFY,
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        ConfirmAttackDoneRequest(
                            lobbyCode = LobbyCode("1052"),
                            playerId = playerOne,
                        ),
                )

            assertEquals(ConfirmAttackDoneErrorCode.PHASE_MISMATCH, error.code)
            assertEquals(TurnPhase.FORTIFY, snapshot.activeTurnPhase)
        }

    @Test
    fun `confirm attack done is rejected when game is paused`() =
        testApplication {
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val (error, snapshot) =
                exerciseFailingConfirm(
                    builder = this,
                    lobbyCode = LobbyCode("1053"),
                    state =
                        attackState(
                            lobbyCode = LobbyCode("1053"),
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerOne,
                            isPaused = true,
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        ConfirmAttackDoneRequest(
                            lobbyCode = LobbyCode("1053"),
                            playerId = playerOne,
                        ),
                )

            assertEquals(ConfirmAttackDoneErrorCode.GAME_PAUSED, error.code)
            assertEquals(true, snapshot.turnState?.isPaused)
        }

    @Test
    fun `confirm attack done rejects mismatched payload player`() =
        testApplication {
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val (error, snapshot) =
                exerciseFailingConfirm(
                    builder = this,
                    lobbyCode = LobbyCode("1054"),
                    state =
                        attackState(
                            lobbyCode = LobbyCode("1054"),
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerOne,
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        ConfirmAttackDoneRequest(
                            lobbyCode = LobbyCode("1054"),
                            playerId = playerTwo,
                        ),
                )

            assertEquals(ConfirmAttackDoneErrorCode.NOT_ACTIVE_PLAYER, error.code)
            assertEquals(TurnPhase.ATTACK, snapshot.activeTurnPhase)
        }

    @Test
    fun `confirm attack done is rejected when lobby does not exist`() =
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
            routingService.start(serverScope)
            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val session =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    session.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ConfirmAttackDoneRequest(
                                        lobbyCode = LobbyCode("1253"),
                                        playerId = PlayerId(1),
                                    ),
                                ),
                        ),
                    )

                    val error =
                        receiveRelevantTestPayload(session.first) as ConfirmAttackDoneErrorResponse

                    assertEquals(ConfirmAttackDoneErrorCode.GAME_NOT_FOUND, error.code)
                    assertNull(receiveRelevantTestPayloadOrNull(session.first))
                    session.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private fun attackState(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        turnPhase: TurnPhase = TurnPhase.ATTACK,
        isPaused: Boolean = false,
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
                        pauseReason =
                            TurnPauseReasons.WAITING_FOR_PLAYER.takeIf { isPaused },
                        pausedPlayerId = activePlayerId.takeIf { isPaused },
                    ),
                status = GameStatus.RUNNING,
            )

    private suspend fun connectSessionWithConnection(
        client: HttpClient,
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

    private fun defaultMapDefinition() =
        at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault()

    private suspend fun exerciseFailingConfirm(
        builder: ApplicationTestBuilder,
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: ConfirmAttackDoneRequest,
    ): Pair<ConfirmAttackDoneErrorResponse, GameState> {
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

        lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = state)
        builder.application {
            module(network)
        }
        routingService.start(serverScope)
        val client =
            builder.createClient {
                install(WebSockets)
            }

        return try {
            coroutineScope {
                val session =
                    connectSessionWithConnection(
                        client = client,
                        network = network,
                        playerId = requesterPlayerId,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                    )

                session.first.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(request),
                    ),
                )

                val error =
                    receiveRelevantTestPayload(session.first) as ConfirmAttackDoneErrorResponse
                assertNull(receiveRelevantTestPayloadOrNull(session.first))
                val snapshot =
                    lobbyManager.getLobby(lobbyCode)?.currentState()
                        ?: error("state missing")
                session.first.close()
                error to snapshot
            }
        } finally {
            routingService.stop()
            lobbyManager.shutdownAll()
            serverScope.cancel()
        }
    }
}
