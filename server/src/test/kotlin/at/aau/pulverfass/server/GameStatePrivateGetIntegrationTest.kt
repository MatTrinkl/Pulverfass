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
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
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

class GameStatePrivateGetIntegrationTest {
    @Test
    fun `player receives own private snapshot with matching state version`() =
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

            val lobbyCode = LobbyCode("PG01")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    privateSnapshotGameState(
                        lobbyCode,
                        listOf(playerOne, playerTwo),
                        stateVersion = 7,
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
                        connectSessionWithPlayer(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                        )

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    GameStatePrivateGetRequest(lobbyCode, playerOne),
                                ),
                        ),
                    )

                    assertEquals(
                        GameStatePrivateGetResponse(
                            lobbyCode = lobbyCode,
                            recipientPlayerId = playerOne,
                            stateVersion = 7,
                            handCards = emptyList(),
                            secretObjectives = emptyList(),
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    assertNull(receivePayloadOrNull(playerOneSession.first))

                    playerOneSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `player cannot request private snapshot of another player`() =
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

            val lobbyCode = LobbyCode("PG02")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    privateSnapshotGameState(
                        lobbyCode,
                        listOf(playerOne, playerTwo),
                        stateVersion = 5,
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
                        connectSessionWithPlayer(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                        )
                    val playerTwoSession =
                        connectSessionWithPlayer(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                        )

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    GameStatePrivateGetRequest(lobbyCode, playerTwo),
                                ),
                        ),
                    )

                    assertEquals(
                        GameStatePrivateGetErrorResponse(
                            code = GameStatePrivateGetErrorCode.REQUESTER_MISMATCH,
                            reason = "Requester '2' passt nicht zur aktuellen Connection '1'.",
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    assertNull(receivePayloadOrNull(playerTwoSession.first))

                    playerOneSession.first.close()
                    playerTwoSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private fun privateSnapshotGameState(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        stateVersion: Long,
    ): GameState =
        GameState(
            lobbyCode = lobbyCode,
            lobbyOwner = players.firstOrNull(),
            players = players,
            playerDisplayNames = players.associateWith { "Player ${it.value}" },
            turnOrder = players,
            stateVersion = stateVersion,
        )

    private suspend fun connectSessionWithPlayer(
        client: io.ktor.client.HttpClient,
        network: ServerNetwork,
        playerId: PlayerId,
        playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
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
        val connectionId = connectedDeferred.await().connectionId
        playersByConnection[connectionId] = playerId
        session to connectionId
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
