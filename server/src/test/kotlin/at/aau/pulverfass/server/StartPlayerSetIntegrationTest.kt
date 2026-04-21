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
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
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

            val lobbyCode = LobbyCode("SP11")
            val host = PlayerId(1)
            val player2 = PlayerId(2)
            val outsider = PlayerId(3)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState = preGameState(lobbyCode, listOf(host, player2), host),
            )
            lobbyManager.createLobby(
                lobbyCode = LobbyCode("SP99"),
                initialState = preGameState(LobbyCode("SP99"), listOf(outsider), outsider),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val hostSession =
                        connectSessionWithConnection(client, network, host, playersByConnection, connectionsByPlayer)
                    val player2Session =
                        connectSessionWithConnection(client, network, player2, playersByConnection, connectionsByPlayer)
                    val outsiderSession =
                        connectSessionWithConnection(client, network, outsider, playersByConnection, connectionsByPlayer)

                    hostSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(StartPlayerSetRequest(lobbyCode, player2, host)),
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
                    assertEquals(player2, lobbyManager.getLobby(lobbyCode)?.currentState()?.configuredStartPlayerId)

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
                    lobbyCode = LobbyCode("SP12"),
                    state = preGameState(LobbyCode("SP12"), listOf(PlayerId(1), PlayerId(2)), PlayerId(1)),
                    requesterPlayerId = PlayerId(2),
                    request = StartPlayerSetRequest(LobbyCode("SP12"), PlayerId(2), PlayerId(2)),
                )

            assertEquals(StartPlayerSetErrorCode.NOT_HOST, result.first.code)
            assertEquals(PlayerId(1), result.second.configuredStartPlayerId)
        }

    @Test
    fun `non member start player gets player not in lobby error`() =
        testApplication {
            val result =
                exerciseFailingSet(
                    lobbyCode = LobbyCode("SP13"),
                    state = preGameState(LobbyCode("SP13"), listOf(PlayerId(1), PlayerId(2)), PlayerId(1)),
                    requesterPlayerId = PlayerId(1),
                    request = StartPlayerSetRequest(LobbyCode("SP13"), PlayerId(99), PlayerId(1)),
                )

            assertEquals(StartPlayerSetErrorCode.PLAYER_NOT_IN_LOBBY, result.first.code)
            assertEquals(PlayerId(1), result.second.configuredStartPlayerId)
        }

    @Test
    fun `setting start player after game started fails`() =
        testApplication {
            val result =
                exerciseFailingSet(
                    lobbyCode = LobbyCode("SP14"),
                    state =
                        preGameState(LobbyCode("SP14"), listOf(PlayerId(1), PlayerId(2)), PlayerId(1))
                            .copy(gameStarted = true, status = GameStatus.RUNNING),
                    requesterPlayerId = PlayerId(1),
                    request = StartPlayerSetRequest(LobbyCode("SP14"), PlayerId(2), PlayerId(1)),
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
                    connectSessionWithConnection(client, network, requesterPlayerId, playersByConnection, connectionsByPlayer)

                requesterSession.first.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(request),
                    ),
                )

                val error = assertIs<StartPlayerSetErrorResponse>(receivePayload(requesterSession.first))
                assertNull(receivePayloadOrNull(requesterSession.first))

                val snapshot = lobbyManager.getLobby(lobbyCode)?.currentState() ?: error("snapshot missing")
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
            val frame = withTimeout(5_000) { session.incoming.receive() }
            assertTrue(frame is Frame.Binary)
            val payload = MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
            if (payload !is GameStateDeltaEvent) {
                return payload
            }
        }
        throw AssertionError("Expected non-delta payload within 10 messages.")
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
            if (payload !is GameStateDeltaEvent) {
                return payload
            }
        }
        return null
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T, "Expected ${T::class.simpleName}, but was ${value?.let { it::class.simpleName }}.")
        return value as T
    }
}
