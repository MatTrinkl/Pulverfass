package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class PhaseOneOrchestrationIntegrationTest {
    private val mapDefinition = MapConfigLoader.loadDefault()

    @Test
    fun `DRAW_CARD to REINFORCEMENTS advance grants reinforcements exactly once`() =
        testApplication {
            val lobbyCode = LobbyCode("1293")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router = MainServerRouter(lobbyManager, DefaultNetworkToLobbyEventMapper())
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { playersByConnection[it] },
                    connectionIdResolver = { connectionsByPlayer[it] },
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningGameState(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
                    ),
            )

            application { module(network) }
            routingService.start(serverScope)

            val client = createClient { install(WebSockets) }

            try {
                coroutineScope {
                    val (p1Session, _) =
                        connectSession(
                            client,
                            network,
                            playerOne,
                            playersByConnection,
                            connectionsByPlayer,
                        )
                    val (p2Session, _) =
                        connectSession(
                            client,
                            network,
                            playerTwo,
                            playersByConnection,
                            connectionsByPlayer,
                        )

                    p1Session.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(lobbyCode, playerOne, TurnPhase.DRAW_CARD),
                                ),
                        ),
                    )

                    // playerTwo receives 5 messages in deterministic order:
                    // 1) delta with TurnStateUpdatedEvent (playerTwo enters REINFORCEMENTS)
                    receiveAnyPayload(p2Session)

                    // 2) delta with exactly one ReinforcementsGrantedEvent — the base grant
                    val grantDelta = receiveAnyPayload(p2Session) as GameStateDeltaEvent
                    val grant = grantDelta.events.single() as ReinforcementsGrantedEvent
                    assertEquals(playerTwo, grant.playerId)
                    assertEquals(0, grant.cardBonus)
                    assertEquals(3, grant.amount)

                    // 3-5) PhaseBoundaryEvent, TurnStateUpdatedEvent, GameStateSnapshotBroadcast
                    receiveAnyPayload(p2Session)
                    receiveAnyPayload(p2Session)
                    receiveAnyPayload(p2Session)

                    // No second grant: the base grant fires exactly once per phase entry
                    assertNull(receiveAnyPayloadOrNull(p2Session))

                    p1Session.close()
                    p2Session.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `game start grants base reinforcements for first active player`() =
        testApplication {
            val lobbyCode = LobbyCode("1294")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val playerThree = PlayerId(3)
            val players = listOf(playerOne, playerTwo, playerThree)

            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router = MainServerRouter(lobbyManager, DefaultNetworkToLobbyEventMapper())
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { playersByConnection[it] },
                    connectionIdResolver = { connectionsByPlayer[it] },
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            val initialState =
                GameState.initial(
                    lobbyCode = lobbyCode,
                    mapDefinition = mapDefinition,
                    players = players,
                    playerDisplayNames = players.associateWith { "Player ${it.value}" },
                ).copy(
                    lobbyOwner = playerOne,
                    activePlayer = null,
                    turnOrder = players,
                    turnState = null,
                    status = GameStatus.WAITING_FOR_PLAYERS,
                )

            application { module(network) }
            lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = initialState)
            routingService.start(serverScope)

            val client = createClient { install(WebSockets) }

            try {
                coroutineScope {
                    val (session, _) =
                        connectSession(
                            client,
                            network,
                            playerOne,
                            playersByConnection,
                            connectionsByPlayer,
                        )

                    session.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(StartGameRequest(lobbyCode)),
                        ),
                    )

                    // Consume the 3 non-delta messages (deltas are skipped by skipGameSync=true):
                    // StartGameResponse, GameStartedEvent, TurnStateUpdatedEvent
                    receiveRelevantTestPayload(session, skipGameSync = true)
                    receiveRelevantTestPayload(session, skipGameSync = true)
                    receiveRelevantTestPayload(session, skipGameSync = true)

                    // The grant is submitted before TurnStateUpdatedEvent, so by the time we
                    // receive TurnStateUpdatedEvent the pending amount is already applied.
                    val finalState =
                        lobbyManager.getLobby(lobbyCode)?.currentState() ?: error("state missing")
                    val activePlayer =
                        finalState.activePlayer ?: error("no active player after start")
                    assertTrue(
                        finalState.pendingReinforcementsFor(activePlayer) >= 3,
                        "Expected base reinforcements >= 3 for active player after game start",
                    )

                    session.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `REINFORCEMENTS to ATTACK advance does not trigger a grant`() =
        testApplication {
            val lobbyCode = LobbyCode("1295")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)

            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router = MainServerRouter(lobbyManager, DefaultNetworkToLobbyEventMapper())
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { playersByConnection[it] },
                    connectionIdResolver = { connectionsByPlayer[it] },
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningGameState(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                    ),
            )

            application { module(network) }
            routingService.start(serverScope)

            val client = createClient { install(WebSockets) }

            try {
                coroutineScope {
                    val (session, _) =
                        connectSession(
                            client,
                            network,
                            playerOne,
                            playersByConnection,
                            connectionsByPlayer,
                        )

                    session.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode,
                                        playerOne,
                                        TurnPhase.REINFORCEMENTS,
                                    ),
                                ),
                        ),
                    )

                    // Wait for TurnAdvanceResponse (skips the delta); server is done by then.
                    receiveRelevantTestPayload(session, skipGameSync = true)

                    // Advancing from REINFORCEMENTS → ATTACK must not grant any pending troops.
                    val state =
                        lobbyManager.getLobby(lobbyCode)?.currentState() ?: error("state missing")
                    assertEquals(0, state.pendingReinforcementsFor(playerOne))

                    session.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private fun runningGameState(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        turnPhase: TurnPhase,
    ): GameState =
        GameState.initial(
            lobbyCode = lobbyCode,
            mapDefinition = mapDefinition,
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
                ),
            status = GameStatus.RUNNING,
        )

    private suspend fun connectSession(
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
    ) = receiveRelevantTestPayload(session)

    private suspend fun receiveAnyPayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ) = receiveRelevantTestPayloadOrNull(
        session = session,
        skipGameSync = false,
        timeoutMillis = 300,
    )
}
