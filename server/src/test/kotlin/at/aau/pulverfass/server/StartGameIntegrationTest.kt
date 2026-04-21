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
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponse
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
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

class StartGameIntegrationTest {
    @Test
    fun `start game request broadcasts game started and turn state to all players`() =
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

            val lobbyCode = LobbyCode("STG1")
            val ownerId = PlayerId(1)
            val player2Id = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode,
                GameState(
                    lobbyCode = lobbyCode,
                    lobbyOwner = ownerId,
                    players = listOf(ownerId, player2Id),
                    playerDisplayNames = mapOf(ownerId to "Owner", player2Id to "Player 2"),
                    activePlayer = ownerId,
                    turnOrder = listOf(ownerId, player2Id),
                    status = GameStatus.WAITING_FOR_PLAYERS,
                ),
            )

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
                    val ownerSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = ownerId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val player2Session =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = player2Id,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    val startRequest = StartGameRequest(lobbyCode = lobbyCode)
                    ownerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(startRequest),
                        ),
                    )

                    assertEquals(StartGameResponse(), receivePayload(ownerSession.first))
                    assertEquals(
                        GameStartedEvent(lobbyCode = lobbyCode),
                        receivePayload(ownerSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = ownerId,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = ownerId,
                        ),
                        receivePayload(ownerSession.first),
                    )
                    assertEquals(
                        GameStartedEvent(lobbyCode = lobbyCode),
                        receivePayload(player2Session.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = ownerId,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = ownerId,
                        ),
                        receivePayload(player2Session.first),
                    )
                    assertNull(receivePayloadOrNull(ownerSession.first))
                    assertNull(receivePayloadOrNull(player2Session.first))
                    assertEquals(2, lobbyManager.getLobby(lobbyCode)?.currentState()?.players?.size)

                    ownerSession.first.close()
                    player2Session.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `start game with insufficient players returns error`() =
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

            val lobbyCode = LobbyCode("STG2")
            val ownerId = PlayerId(1)
            lobbyManager.createLobby(
                lobbyCode,
                GameState(
                    lobbyCode = lobbyCode,
                    lobbyOwner = ownerId,
                    players = listOf(ownerId),
                    playerDisplayNames = mapOf(ownerId to "Owner"),
                    activePlayer = ownerId,
                    turnOrder = listOf(ownerId),
                    status = GameStatus.WAITING_FOR_PLAYERS,
                ),
            )

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
                    val ownerSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = ownerId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    val startRequest = StartGameRequest(lobbyCode = lobbyCode)
                    ownerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(startRequest),
                        ),
                    )

                    val decoded = receivePayload(ownerSession.first)
                    assertTrue(decoded is StartGameErrorResponse)
                    assertNull(receivePayloadOrNull(ownerSession.first))
                    assertEquals(1, lobbyManager.getLobby(lobbyCode)?.currentState()?.players?.size)

                    ownerSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
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
    ) = run {
        repeat(10) {
            val frame = withTimeout(5_000) { session.incoming.receive() }
            assertTrue(frame is Frame.Binary)
            val payload = MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
            if (payload !is GameStateDeltaEvent) {
                return@run payload
            }
        }
        throw AssertionError("Expected non-delta payload within 10 messages.")
    }

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ) = run {
        repeat(5) {
            val frame =
                withTimeoutOrNull(200) {
                    session.incoming.receive()
                } ?: return@run null
            assertTrue(frame is Frame.Binary)
            val payload = MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
            if (payload !is GameStateDeltaEvent) {
                return@run payload
            }
        }
        null
    }
}
