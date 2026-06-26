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
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class StartPlayerSetIntegrationTest {
    @Test
    fun `host can set start player and lobby receives turn state update`() =
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

            val lobbyCode = LobbyCode("1344")
            val host = PlayerId(1)
            val player2 = PlayerId(2)
            val outsider = PlayerId(3)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState = preGameState(lobbyCode, listOf(host, player2), host),
            )
            lobbyManager.createLobby(
                lobbyCode = LobbyCode("1350"),
                initialState = preGameState(LobbyCode("1350"), listOf(outsider), outsider),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val hostSession =
                        connectSessionWithConnection(
                            client,
                            network,
                            host,
                            playersByConnection,
                            connectionsByPlayer,
                        )
                    val player2Session =
                        connectSessionWithConnection(
                            client,
                            network,
                            player2,
                            playersByConnection,
                            connectionsByPlayer,
                        )
                    val outsiderSession =
                        connectSessionWithConnection(
                            client,
                            network,
                            outsider,
                            playersByConnection,
                            connectionsByPlayer,
                        )

                    hostSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    StartPlayerSetRequest(lobbyCode, player2, host),
                                ),
                        ),
                    )

                    assertEquals(
                        StartPlayerSetResponse(lobbyCode, player2),
                        receivePayload(hostSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = player2,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = player2,
                        ),
                        receivePayload(hostSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = player2,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = player2,
                        ),
                        receivePayload(player2Session.first),
                    )
                    assertNull(receivePayloadOrNull(outsiderSession.first))
                    assertEquals(
                        player2,
                        lobbyManager.getLobby(lobbyCode)?.currentState()?.configuredStartPlayerId,
                    )

                    hostSession.first.close()
                    player2Session.first.close()
                    outsiderSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `non host gets not host error and no state change`() =
        testApplication {
            val result =
                exerciseFailingSet(
                    lobbyCode = LobbyCode("1345"),
                    state =
                        preGameState(
                            LobbyCode("1345"),
                            listOf(PlayerId(1), PlayerId(2)),
                            PlayerId(1),
                        ),
                    requesterPlayerId = PlayerId(2),
                    request = StartPlayerSetRequest(LobbyCode("1345"), PlayerId(2), PlayerId(2)),
                )

            assertEquals(StartPlayerSetErrorCode.NOT_HOST, result.first.code)
            assertEquals(PlayerId(1), result.second.configuredStartPlayerId)
        }

    @Test
    fun `non member start player gets player not in lobby error`() =
        testApplication {
            val result =
                exerciseFailingSet(
                    lobbyCode = LobbyCode("1346"),
                    state =
                        preGameState(
                            LobbyCode("1346"),
                            listOf(PlayerId(1), PlayerId(2)),
                            PlayerId(1),
                        ),
                    requesterPlayerId = PlayerId(1),
                    request = StartPlayerSetRequest(LobbyCode("1346"), PlayerId(99), PlayerId(1)),
                )

            assertEquals(StartPlayerSetErrorCode.PLAYER_NOT_IN_LOBBY, result.first.code)
            assertEquals(PlayerId(1), result.second.configuredStartPlayerId)
        }

    @Test
    fun `setting start player after game started fails`() =
        testApplication {
            val result =
                exerciseFailingSet(
                    lobbyCode = LobbyCode("1347"),
                    state =
                        preGameState(
                            LobbyCode("1347"),
                            listOf(PlayerId(1), PlayerId(2)),
                            PlayerId(1),
                        )
                            .copy(gameStarted = true, status = GameStatus.RUNNING),
                    requesterPlayerId = PlayerId(1),
                    request = StartPlayerSetRequest(LobbyCode("1347"), PlayerId(2), PlayerId(1)),
                )

            assertEquals(StartPlayerSetErrorCode.GAME_ALREADY_STARTED, result.first.code)
            assertEquals(PlayerId(1), result.second.configuredStartPlayerId)
        }

    private suspend fun ApplicationTestBuilder.exerciseFailingSet(
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: StartPlayerSetRequest,
    ): Pair<StartPlayerSetErrorResponse, GameState> {
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
                        client,
                        network,
                        requesterPlayerId,
                        playersByConnection,
                        connectionsByPlayer,
                    )

                requesterSession.first.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(request),
                    ),
                )

                val error =
                    assertIs<StartPlayerSetErrorResponse>(receivePayload(requesterSession.first))
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

    private fun preGameState(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        configuredStartPlayerId: PlayerId,
    ): GameState =
        GameState(
            lobbyCode = lobbyCode,
            lobbyOwner = players.firstOrNull(),
            players = players,
            playerDisplayNames = players.associateWith { "Player ${it.value}" },
            activePlayer = configuredStartPlayerId,
            configuredStartPlayerId = configuredStartPlayerId,
            turnOrder = players,
            turnNumber = 1,
            turnState =
                TurnState(
                    activePlayerId = configuredStartPlayerId,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    turnCount = 1,
                    startPlayerId = configuredStartPlayerId,
                ),
            status = GameStatus.WAITING_FOR_PLAYERS,
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

    private suspend fun receivePayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any = receiveRelevantTestPayload(session = session, skipGameSync = true)

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any? =
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
