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
import at.aau.pulverfass.shared.lobby.state.PendingReinforcements
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.request.ConfirmReinforcementsDoneRequest
import at.aau.pulverfass.shared.message.lobby.response.ConfirmReinforcementsDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponse
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

class ConfirmReinforcementsDoneIntegrationTest {
    @Test
    fun `pending zero advances to attack`() =
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

            val lobbyCode = LobbyCode("CRI1")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    reinforcementState(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        pendingAmount = 0,
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
                                    ConfirmReinforcementsDoneRequest(
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
                            turnPhase = TurnPhase.ATTACK,
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

                    assertEquals(expectedDelta, receiveAnyPayload(actorSession.first))
                    assertEquals(
                        ConfirmReinforcementsDoneResponse(lobbyCode),
                        receiveAnyPayload(actorSession.first),
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
                        receiveAnyPayload(actorSession.first),
                    )
                    assertEquals(expectedUpdate, receiveAnyPayload(actorSession.first))

                    assertEquals(expectedDelta, receiveAnyPayload(watcherSession.first))
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.REINFORCEMENTS,
                            nextPhase = TurnPhase.ATTACK,
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(watcherSession.first),
                    )
                    assertEquals(expectedUpdate, receiveAnyPayload(watcherSession.first))

                    val updatedState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(TurnPhase.ATTACK, updatedState.activeTurnPhase)
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
    fun `pending greater than zero fails`() =
        testApplication {
            val lobbyCode = LobbyCode("CRI2")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val initialState =
                reinforcementState(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    pendingAmount = 2,
                )

            val (error, snapshot) =
                exerciseFailingConfirm(
                    lobbyCode = lobbyCode,
                    state = initialState,
                    requesterPlayerId = playerOne,
                    request =
                        ConfirmReinforcementsDoneRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                        ),
                )

            assertEquals(
                ConfirmReinforcementsDoneErrorCode.PENDING_REINFORCEMENTS_REMAINING,
                error.code,
            )
            assertEquals(TurnPhase.REINFORCEMENTS, snapshot.activeTurnPhase)
            assertEquals(2, snapshot.pendingReinforcementsFor(playerOne))
        }

    private suspend fun ApplicationTestBuilder.exerciseFailingConfirm(
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: ConfirmReinforcementsDoneRequest,
    ): Pair<ConfirmReinforcementsDoneErrorResponse, GameState> {
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
                    assertIs<ConfirmReinforcementsDoneErrorResponse>(
                        receiveRelevantTestPayload(requesterSession.first),
                    )
                assertNull(receiveRelevantTestPayloadOrNull(requesterSession.first))

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

    private fun reinforcementState(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        pendingAmount: Int,
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
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = players.first(),
                    ),
                status = GameStatus.RUNNING,
                pendingReinforcements = PendingReinforcements(activePlayerId, pendingAmount),
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

    private suspend fun receiveAnyPayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any = receiveRelevantTestPayload(session)

    private inline fun <reified T> assertIs(value: Any?): T {
        require(value is T) {
            "Expected ${T::class.simpleName}, but was ${value?.let { it::class.simpleName }}."
        }
        return value
    }

    private fun defaultMapDefinition() =
        at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault()
}
