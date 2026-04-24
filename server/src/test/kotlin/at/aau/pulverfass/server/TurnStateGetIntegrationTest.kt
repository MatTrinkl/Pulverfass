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
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
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

class TurnStateGetIntegrationTest {
    @Test
    fun `turn state get returns current authoritative turn state`() =
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
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("TS01")
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(PlayerId(1), PlayerId(2)),
                        activePlayerId = PlayerId(2),
                        turnPhase = TurnPhase.FORTIFY,
                        turnCount = 3,
                        startPlayerId = PlayerId(1),
                        isPaused = true,
                        pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                        pausedPlayerId = PlayerId(2),
                    ),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionAndConnection = connectSession(client, network)

                    sessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(TurnStateGetRequest(lobbyCode)),
                        ),
                    )

                    assertEquals(
                        TurnStateGetResponse(
                            lobbyCode = lobbyCode,
                            activePlayerId = PlayerId(2),
                            turnPhase = TurnPhase.FORTIFY,
                            turnCount = 3,
                            startPlayerId = PlayerId(1),
                            isPaused = true,
                            pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                            pausedPlayerId = PlayerId(2),
                        ),
                        receivePayload(sessionAndConnection.first),
                    )
                    assertNull(receivePayloadOrNull(sessionAndConnection.first))

                    sessionAndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `turn state get returns error for unknown lobby`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router =
                MainServerRouter(
                    lobbyManager = lobbyManager,
                    mapper = DefaultNetworkToLobbyEventMapper(),
                )
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { null },
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
                    val sessionAndConnection = connectSession(client, network)

                    sessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(TurnStateGetRequest(LobbyCode("TS99"))),
                        ),
                    )

                    assertEquals(
                        TurnStateGetErrorResponse(
                            code = TurnStateGetErrorCode.GAME_NOT_FOUND,
                            reason = "Lobby 'TS99' wurde nicht gefunden.",
                        ),
                        receivePayload(sessionAndConnection.first),
                    )
                    assertNull(receivePayloadOrNull(sessionAndConnection.first))

                    sessionAndConnection.first.close()
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
        turnCount: Int,
        startPlayerId: PlayerId,
        isPaused: Boolean = false,
        pauseReason: String? = null,
        pausedPlayerId: PlayerId? = null,
    ): GameState =
        GameState(
            lobbyCode = lobbyCode,
            lobbyOwner = players.firstOrNull(),
            players = players,
            playerDisplayNames = players.associateWith { "Player ${it.value}" },
            activePlayer = activePlayerId,
            turnOrder = players,
            turnNumber = turnCount,
            turnState =
                TurnState(
                    activePlayerId = activePlayerId,
                    turnPhase = turnPhase,
                    turnCount = turnCount,
                    startPlayerId = startPlayerId,
                    isPaused = isPaused,
                    pauseReason = pauseReason,
                    pausedPlayerId = pausedPlayerId,
                ),
            status = GameStatus.RUNNING,
        )

    private suspend fun connectSession(
        client: io.ktor.client.HttpClient,
        network: ServerNetwork,
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
        session to connectedDeferred.await().connectionId
    }

    private suspend fun receivePayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any {
        val frame = withTimeout(5_000) { session.incoming.receive() }
        assertTrue(frame is Frame.Binary)
        return MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
    }

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any? =
        withTimeoutOrNull(500) {
            val frame = session.incoming.receive()
            assertTrue(frame is Frame.Binary)
            MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
        }
}
