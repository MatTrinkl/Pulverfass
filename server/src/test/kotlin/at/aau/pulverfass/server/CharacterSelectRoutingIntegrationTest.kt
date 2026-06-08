package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.event.CharacterSelectedBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CharacterSelectRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.response.CharacterSelectResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CharacterSelectErrorResponse
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
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

class CharacterSelectRoutingIntegrationTest {
    @Test
    fun `successful character select sends response and broadcasts to all lobby members`() =
        testApplication {
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = MainServerRouter(lobbyManager, DefaultNetworkToLobbyEventMapper()),
                    lobbyManager = lobbyManager,
                    playerIdResolver = { id -> playersByConnection[id] },
                    connectionIdResolver = { pid -> connectionsByPlayer[pid] },
                )

            application { module(network) }

            val lobbyCode = LobbyCode("CS10")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState = preGameState(lobbyCode, playerOne, playerTwo),
            )
            routingService.start(serverScope)
            val client = createClient { install(WebSockets) }

            try {
                coroutineScope {
                    val sessionOne =
                        connectAndRegister(
                            client,
                            network,
                            playerOne,
                            playersByConnection,
                            connectionsByPlayer,
                        )
                    val sessionTwo =
                        connectAndRegister(
                            client,
                            network,
                            playerTwo,
                            playersByConnection,
                            connectionsByPlayer,
                        )

                    sessionOne.send(
                        Frame.Binary(
                            true,
                            MessageCodec.encode(
                                CharacterSelectRequest(lobbyCode, playerOne, "warrior"),
                            ),
                        ),
                    )

                    val response = receiveRelevantTestPayload(sessionOne)
                    assertTrue(response is CharacterSelectResponse)
                    assertEquals("warrior", (response as CharacterSelectResponse).characterId)
                    assertEquals(lobbyCode, response.lobbyCode)

                    val broadcastOne = receiveRelevantTestPayload(sessionOne)
                    val broadcastTwo = receiveRelevantTestPayload(sessionTwo)
                    assertTrue(broadcastOne is CharacterSelectedBroadcast)
                    assertTrue(broadcastTwo is CharacterSelectedBroadcast)
                    assertEquals(
                        "warrior",
                        (broadcastOne as CharacterSelectedBroadcast).characterId,
                    )
                    assertEquals(playerOne, broadcastOne.playerId)

                    sessionOne.close()
                    sessionTwo.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `joining player receives existing character selections`() =
        testApplication {
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = MainServerRouter(lobbyManager, DefaultNetworkToLobbyEventMapper()),
                    lobbyManager = lobbyManager,
                    playerIdResolver = { id -> playersByConnection[id] },
                    connectionIdResolver = { pid -> connectionsByPlayer[pid] },
                )

            application { module(network) }

            val lobbyCode = LobbyCode("CS15")
            val playerOne = PlayerId(11)
            val playerTwo = PlayerId(12)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    GameState(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne),
                        playerDisplayNames = mapOf(playerOne to "Alice"),
                        lobbyOwner = playerOne,
                    ),
            )
            routingService.start(serverScope)
            val client = createClient { install(WebSockets) }

            try {
                coroutineScope {
                    val sessionOne =
                        connectAndRegister(
                            client,
                            network,
                            playerOne,
                            playersByConnection,
                            connectionsByPlayer,
                        )
                    sessionOne.send(
                        Frame.Binary(
                            true,
                            MessageCodec.encode(
                                CharacterSelectRequest(lobbyCode, playerOne, "character_04"),
                            ),
                        ),
                    )
                    receiveRelevantTestPayload(sessionOne)
                    receiveRelevantTestPayload(sessionOne)

                    val sessionTwo =
                        connectAndRegister(
                            client,
                            network,
                            playerTwo,
                            playersByConnection,
                            connectionsByPlayer,
                        )
                    sessionTwo.send(
                        Frame.Binary(
                            true,
                            MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Bob")),
                        ),
                    )

                    val joinResponse = receiveRelevantTestPayload(sessionTwo, skipGameSync = true)
                    val existingPlayer = receiveRelevantTestPayload(sessionTwo, skipGameSync = true)
                    val characterReplay =
                        receiveRelevantTestPayload(sessionTwo, skipGameSync = true)
                    val joinedPlayer = receiveRelevantTestPayload(sessionTwo, skipGameSync = true)

                    assertEquals(JoinLobbyResponse(lobbyCode), joinResponse)
                    assertTrue(existingPlayer is PlayerJoinedLobbyEvent)
                    assertEquals(playerOne, (existingPlayer as PlayerJoinedLobbyEvent).playerId)
                    assertTrue(characterReplay is CharacterSelectedBroadcast)
                    assertEquals(
                        CharacterSelectedBroadcast(lobbyCode, playerOne, "character_04"),
                        characterReplay,
                    )
                    assertTrue(joinedPlayer is PlayerJoinedLobbyEvent)
                    assertEquals(playerTwo, (joinedPlayer as PlayerJoinedLobbyEvent).playerId)

                    sessionOne.close()
                    sessionTwo.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `second player selecting same character receives error response`() =
        testApplication {
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = MainServerRouter(lobbyManager, DefaultNetworkToLobbyEventMapper()),
                    lobbyManager = lobbyManager,
                    playerIdResolver = { id -> playersByConnection[id] },
                    connectionIdResolver = { pid -> connectionsByPlayer[pid] },
                )

            application { module(network) }

            val lobbyCode = LobbyCode("CS20")
            val playerOne = PlayerId(3)
            val playerTwo = PlayerId(4)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState = preGameState(lobbyCode, playerOne, playerTwo),
            )
            routingService.start(serverScope)
            val client = createClient { install(WebSockets) }

            try {
                coroutineScope {
                    val sessionOne =
                        connectAndRegister(
                            client,
                            network,
                            playerOne,
                            playersByConnection,
                            connectionsByPlayer,
                        )
                    val sessionTwo =
                        connectAndRegister(
                            client,
                            network,
                            playerTwo,
                            playersByConnection,
                            connectionsByPlayer,
                        )

                    sessionOne.send(
                        Frame.Binary(
                            true,
                            MessageCodec.encode(
                                CharacterSelectRequest(lobbyCode, playerOne, "character_04"),
                            ),
                        ),
                    )
                    receiveRelevantTestPayload(sessionOne)
                    receiveRelevantTestPayload(sessionOne)
                    receiveRelevantTestPayload(sessionTwo)

                    sessionTwo.send(
                        Frame.Binary(
                            true,
                            MessageCodec.encode(
                                CharacterSelectRequest(lobbyCode, playerTwo, "character_04"),
                            ),
                        ),
                    )
                    val errorResponse = receiveRelevantTestPayload(sessionTwo)
                    assertTrue(errorResponse is CharacterSelectErrorResponse)

                    sessionOne.close()
                    sessionTwo.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `request with no registered player is silently dropped`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = MainServerRouter(lobbyManager, DefaultNetworkToLobbyEventMapper()),
                    lobbyManager = lobbyManager,
                    playerIdResolver = { null },
                    connectionIdResolver = { null },
                )

            application { module(network) }

            val lobbyCode = LobbyCode("CS30")
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState = preGameState(lobbyCode, PlayerId(5), PlayerId(6)),
            )
            routingService.start(serverScope)
            val client = createClient { install(WebSockets) }

            try {
                coroutineScope {
                    val session = client.webSocketSession("/ws")
                    receiveTestConnectionToken(session)

                    session.send(
                        Frame.Binary(
                            true,
                            MessageCodec.encode(
                                CharacterSelectRequest(lobbyCode, PlayerId(5), "character_03"),
                            ),
                        ),
                    )

                    val result =
                        receiveRelevantTestPayloadOrNull(
                            session = session,
                            timeoutMillis = 250,
                        )
                    assertNull(result)

                    session.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `player can switch character and previous one becomes available for others`() =
        testApplication {
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = MainServerRouter(lobbyManager, DefaultNetworkToLobbyEventMapper()),
                    lobbyManager = lobbyManager,
                    playerIdResolver = { id -> playersByConnection[id] },
                    connectionIdResolver = { pid -> connectionsByPlayer[pid] },
                )

            application { module(network) }

            val lobbyCode = LobbyCode("CS40")
            val playerOne = PlayerId(7)
            val playerTwo = PlayerId(8)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState = preGameState(lobbyCode, playerOne, playerTwo),
            )
            routingService.start(serverScope)
            val client = createClient { install(WebSockets) }

            try {
                coroutineScope {
                    val sessionOne =
                        connectAndRegister(
                            client,
                            network,
                            playerOne,
                            playersByConnection,
                            connectionsByPlayer,
                        )
                    val sessionTwo =
                        connectAndRegister(
                            client,
                            network,
                            playerTwo,
                            playersByConnection,
                            connectionsByPlayer,
                        )

                    sessionOne.send(
                        Frame.Binary(
                            true,
                            MessageCodec.encode(
                                CharacterSelectRequest(lobbyCode, playerOne, "character_04"),
                            ),
                        ),
                    )
                    receiveRelevantTestPayload(sessionOne)
                    receiveRelevantTestPayload(sessionOne)
                    receiveRelevantTestPayload(sessionTwo)

                    sessionOne.send(
                        Frame.Binary(
                            true,
                            MessageCodec.encode(
                                CharacterSelectRequest(lobbyCode, playerOne, "warrior"),
                            ),
                        ),
                    )
                    receiveRelevantTestPayload(sessionOne)
                    receiveRelevantTestPayload(sessionOne)
                    receiveRelevantTestPayload(sessionTwo)

                    sessionTwo.send(
                        Frame.Binary(
                            true,
                            MessageCodec.encode(
                                CharacterSelectRequest(lobbyCode, playerTwo, "character_04"),
                            ),
                        ),
                    )
                    val response = receiveRelevantTestPayload(sessionTwo)
                    assertTrue(response is CharacterSelectResponse)
                    assertEquals("character_04", (response as CharacterSelectResponse).characterId)

                    sessionOne.close()
                    sessionTwo.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private suspend fun connectAndRegister(
        client: HttpClient,
        network: ServerNetwork,
        playerId: PlayerId,
        playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
        connectionsByPlayer: ConcurrentHashMap<PlayerId, ConnectionId>,
    ): DefaultClientWebSocketSession {
        val session = client.webSocketSession("/ws")
        val sessionToken = receiveTestConnectionToken(session)
        val connectionId = awaitTestConnectionId(network, sessionToken)
        playersByConnection[connectionId] = playerId
        connectionsByPlayer[playerId] = connectionId
        return session
    }

    private fun preGameState(
        lobbyCode: LobbyCode,
        playerOne: PlayerId,
        playerTwo: PlayerId,
    ): GameState =
        GameState(
            lobbyCode = lobbyCode,
            players = listOf(playerOne, playerTwo),
            playerDisplayNames = mapOf(playerOne to "Alice", playerTwo to "Bob"),
            lobbyOwner = playerOne,
        )
}
