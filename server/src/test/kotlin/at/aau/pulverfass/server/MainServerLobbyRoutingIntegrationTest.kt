package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.KickPlayerResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MainServerLobbyRoutingIntegrationTest {
    @Test
    fun `map get request returns full snapshot for requesting client`() =
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
                )

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("MP12")
            val playerId = PlayerId(1)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState = createMappedGameState(lobbyCode, playerId),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(MapGetRequest(lobbyCode)),
                        ),
                    )

                    val payload = receivePayload(sessionAndConnection.first)
                    val response = assertIs<MapGetResponse>(payload)

                    assertEquals(lobbyCode, response.lobbyCode)
                    assertEquals(1, response.schemaVersion)
                    assertEquals(defaultMapDefinition().mapHash, response.mapHash)
                    assertEquals(3, response.stateVersion)
                    assertEquals(23, response.definition.territories.size)
                    assertEquals(23, response.territoryStates.size)
                    assertEquals(
                        PlayerId(1),
                        response.territoryStates
                            .first { it.territoryId == TerritoryId("argentinien") }
                            .ownerId,
                    )
                    assertEquals(
                        5,
                        response.territoryStates
                            .first { it.territoryId == TerritoryId("argentinien") }
                            .troopCount,
                    )
                    assertTrue(
                        response.definition.territories
                            .first { it.territoryId == TerritoryId("brasilien") }
                            .edges
                            .any { it.targetId == TerritoryId("sahara") },
                    )

                    sessionAndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `module with lobby runtime loads default map at startup and returns it via map get`() =
        testApplication {
            val network = ServerNetwork()

            application {
                moduleWithLobbyRuntime(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            coroutineScope {
                val session = client.webSocketSession("/ws")

                try {
                    session.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(CreateLobbyRequest),
                        ),
                    )
                    val createResponse = assertIs<CreateLobbyResponse>(receivePayload(session))

                    session.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    JoinLobbyRequest(createResponse.lobbyCode, "Alice"),
                                ),
                        ),
                    )

                    assertEquals(
                        JoinLobbyResponse(createResponse.lobbyCode),
                        receivePayload(session),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(
                            lobbyCode = createResponse.lobbyCode,
                            playerId = PlayerId(1),
                            playerDisplayName = "Alice",
                            isHost = true,
                        ),
                        receivePayload(session),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = createResponse.lobbyCode,
                            activePlayerId = PlayerId(1),
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = PlayerId(1),
                        ),
                        receivePayload(session),
                    )

                    session.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(MapGetRequest(createResponse.lobbyCode)),
                        ),
                    )

                    val response = assertIs<MapGetResponse>(receivePayload(session))

                    assertEquals(createResponse.lobbyCode, response.lobbyCode)
                    assertEquals(1, response.schemaVersion)
                    assertEquals(defaultMapDefinition().mapHash, response.mapHash)
                    assertEquals(1, response.stateVersion)
                    assertEquals(23, response.definition.territories.size)
                    assertEquals(6, response.definition.continents.size)
                    assertEquals(23, response.territoryStates.size)
                    assertTrue(
                        response.territoryStates.all { it.ownerId == null && it.troopCount == 0 },
                    )
                    assertTrue(
                        response.definition.territories
                            .first { it.territoryId == TerritoryId("brasilien") }
                            .edges
                            .any { it.targetId == TerritoryId("sahara") },
                    )
                } finally {
                    session.close()
                }
            }
        }

    @Test
    fun `reconnect can recover consistent map snapshot after missed events`() =
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
                )

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("RC12")
            val playerId = PlayerId(1)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    GameState.initial(
                        lobbyCode = lobbyCode,
                        mapDefinition = defaultMapDefinition(),
                        players = listOf(playerId),
                        playerDisplayNames = mapOf(playerId to "Reconnect"),
                    ),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val firstSessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    firstSessionAndConnection.first.close()
                    playersByConnection.remove(firstSessionAndConnection.second)
                    connectionsByPlayer.remove(playerId)

                    lobbyManager.submit(
                        TerritoryOwnerChangedEvent(
                            lobbyCode = lobbyCode,
                            territoryId = TerritoryId("argentinien"),
                            ownerId = playerId,
                        ),
                    )
                    lobbyManager.submit(
                        TerritoryTroopsChangedEvent(
                            lobbyCode = lobbyCode,
                            territoryId = TerritoryId("argentinien"),
                            troopCount = 6,
                        ),
                    )

                    val reconnectedSessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    reconnectedSessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(MapGetRequest(lobbyCode)),
                        ),
                    )

                    val response =
                        assertIs<MapGetResponse>(
                            receivePayload(reconnectedSessionAndConnection.first),
                        )

                    assertEquals(lobbyCode, response.lobbyCode)
                    assertEquals(2, response.stateVersion)
                    assertEquals(defaultMapDefinition().mapHash, response.mapHash)
                    assertEquals(
                        playerId,
                        response.territoryStates
                            .first { it.territoryId == TerritoryId("argentinien") }
                            .ownerId,
                    )
                    assertEquals(
                        6,
                        response.territoryStates
                            .first { it.territoryId == TerritoryId("argentinien") }
                            .troopCount,
                    )

                    reconnectedSessionAndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `map state events are broadcast to lobby members only in order with state version`() =
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
                )

            application {
                module(network)
            }

            val lobbyA = LobbyCode("DL12")
            val lobbyB = LobbyCode("DL34")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val playerThree = PlayerId(3)
            lobbyManager.createLobby(
                lobbyCode = lobbyA,
                initialState =
                    GameState.initial(
                        lobbyCode = lobbyA,
                        mapDefinition = defaultMapDefinition(),
                        players = listOf(playerOne, playerTwo),
                        playerDisplayNames =
                            mapOf(
                                playerOne to "Alice",
                                playerTwo to "Bob",
                            ),
                    ),
            )
            lobbyManager.createLobby(
                lobbyCode = lobbyB,
                initialState =
                    GameState.initial(
                        lobbyCode = lobbyB,
                        mapDefinition = defaultMapDefinition(),
                        players = listOf(playerThree),
                        playerDisplayNames = mapOf(playerThree to "Carol"),
                    ),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionOneAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionTwoAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionThreeAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerThree,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    lobbyManager.submit(
                        TerritoryOwnerChangedEvent(
                            lobbyCode = lobbyA,
                            territoryId = TerritoryId("argentinien"),
                            ownerId = playerOne,
                        ),
                    )
                    lobbyManager.submit(
                        TerritoryTroopsChangedEvent(
                            lobbyCode = lobbyA,
                            territoryId = TerritoryId("argentinien"),
                            troopCount = 7,
                        ),
                    )

                    assertEquals(
                        TerritoryOwnerChangedEvent(
                            lobbyCode = lobbyA,
                            territoryId = TerritoryId("argentinien"),
                            ownerId = playerOne,
                            stateVersion = 1,
                        ),
                        receivePayload(sessionOneAndConnection.first),
                    )
                    assertEquals(
                        TerritoryTroopsChangedEvent(
                            lobbyCode = lobbyA,
                            territoryId = TerritoryId("argentinien"),
                            troopCount = 7,
                            stateVersion = 2,
                        ),
                        receivePayload(sessionOneAndConnection.first),
                    )

                    assertEquals(
                        TerritoryOwnerChangedEvent(
                            lobbyCode = lobbyA,
                            territoryId = TerritoryId("argentinien"),
                            ownerId = playerOne,
                            stateVersion = 1,
                        ),
                        receivePayload(sessionTwoAndConnection.first),
                    )
                    assertEquals(
                        TerritoryTroopsChangedEvent(
                            lobbyCode = lobbyA,
                            territoryId = TerritoryId("argentinien"),
                            troopCount = 7,
                            stateVersion = 2,
                        ),
                        receivePayload(sessionTwoAndConnection.first),
                    )

                    assertNull(receivePayloadOrNull(sessionThreeAndConnection.first))

                    sessionOneAndConnection.first.close()
                    sessionTwoAndConnection.first.close()
                    sessionThreeAndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `turn advance request broadcasts game state delta with correct version range`() =
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
                )

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("DG12")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    GameState(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        playerDisplayNames =
                            mapOf(
                                playerOne to "Alice",
                                playerTwo to "Bob",
                            ),
                        activePlayer = playerOne,
                        configuredStartPlayerId = playerOne,
                        turnOrder = listOf(playerOne, playerTwo),
                        turnNumber = 1,
                        turnState =
                            TurnState(
                                activePlayerId = playerOne,
                                turnPhase = TurnPhase.REINFORCEMENTS,
                                turnCount = 1,
                                startPlayerId = playerOne,
                            ),
                        gameStarted = true,
                        status = GameStatus.RUNNING,
                    ),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionOneAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionTwoAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionOneAndConnection.first.send(
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

                    val requesterDelta =
                        receivePayloadOfType<GameStateDeltaEvent>(sessionOneAndConnection.first)
                    val otherPlayerDelta =
                        receivePayloadOfType<GameStateDeltaEvent>(sessionTwoAndConnection.first)

                    val expectedEvent =
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
                            events = listOf(expectedEvent),
                        )

                    assertEquals(expectedDelta, requesterDelta)
                    assertEquals(expectedDelta, otherPlayerDelta)

                    sessionOneAndConnection.first.close()
                    sessionTwoAndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `map get request returns error for unknown lobby`() =
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
                    val sessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(MapGetRequest(LobbyCode("ZZ99"))),
                        ),
                    )

                    val payload = receivePayload(sessionAndConnection.first)
                    val error = assertIs<MapGetErrorResponse>(payload)

                    assertEquals(MapGetErrorCode.GAME_NOT_FOUND, error.code)
                    assertEquals("Lobby 'ZZ99' wurde nicht gefunden.", error.reason)

                    sessionAndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `server websocket packets werden über router in mehrere lobbys korrekt verteilt`() =
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
            val routedPackets = AtomicInteger(0)
            val routingErrors = AtomicInteger(0)
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
                    hooks =
                        MainServerLobbyRoutingServiceHooks(
                            onRouted = { routedPackets.incrementAndGet() },
                            onRoutingError = { _, _ -> routingErrors.incrementAndGet() },
                        ),
                )

            application {
                module(network)
            }

            val lobbyA = LobbyCode("AB12")
            val lobbyB = LobbyCode("CD34")
            lobbyManager.createLobby(lobbyA)
            lobbyManager.createLobby(lobbyB)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionA1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionA2AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(2),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionB1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(3),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionA1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Alice")),
                        ),
                    )
                    sessionA2AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Bob")),
                        ),
                    )
                    sessionB1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyB, "Carol")),
                        ),
                    )

                    waitUntilProcessed(lobbyManager, lobbyA, expectedCount = 2)
                    waitUntilProcessed(lobbyManager, lobbyB, expectedCount = 1)

                    val stateA = lobbyManager.getLobby(lobbyA)?.currentState()
                    val stateB = lobbyManager.getLobby(lobbyB)?.currentState()
                    assertEquals(2, stateA?.processedEventCount)
                    assertEquals(1, stateB?.processedEventCount)
                    assertEquals(setOf(PlayerId(1), PlayerId(2)), stateA?.players?.toSet())
                    assertEquals(setOf(PlayerId(3)), stateB?.players?.toSet())
                    assertEquals(3, routedPackets.get())
                    assertEquals(0, routingErrors.get())

                    sessionA1AndConnection.first.close()
                    sessionA2AndConnection.first.close()
                    sessionB1AndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `failed join sends join error response to requesting client`() =
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
                    val sessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    JoinLobbyRequest(LobbyCode("ZZ99"), "Alice"),
                                ),
                        ),
                    )

                    val payload = receivePayload(sessionAndConnection.first)

                    assertIs<JoinLobbyErrorResponse>(payload)
                    assertEquals(
                        JoinLobbyErrorResponse("Lobby 'ZZ99' wurde nicht gefunden."),
                        payload,
                    )

                    sessionAndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `successful join sends response and lobby scoped broadcast`() =
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
                )

            application {
                module(network)
            }

            val lobbyA = LobbyCode("AB12")
            val lobbyB = LobbyCode("CD34")
            lobbyManager.createLobby(lobbyA)
            lobbyManager.createLobby(lobbyB)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionA1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionA2AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(2),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionB1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(3),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionA1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Alice")),
                        ),
                    )
                    assertEquals(
                        JoinLobbyResponse(lobbyA),
                        receivePayload(sessionA1AndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(1), "Alice", isHost = true),
                        receivePayload(sessionA1AndConnection.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyA,
                            activePlayerId = PlayerId(1),
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = PlayerId(1),
                        ),
                        receivePayload(sessionA1AndConnection.first),
                    )

                    sessionB1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyB, "Carol")),
                        ),
                    )
                    assertEquals(
                        JoinLobbyResponse(lobbyB),
                        receivePayload(sessionB1AndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyB, PlayerId(3), "Carol", isHost = true),
                        receivePayload(sessionB1AndConnection.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyB,
                            activePlayerId = PlayerId(3),
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = PlayerId(3),
                        ),
                        receivePayload(sessionB1AndConnection.first),
                    )

                    sessionA2AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Bob")),
                        ),
                    )

                    val joinerResponse = receivePayload(sessionA2AndConnection.first)
                    val joinerExistingMemberEvent = receivePayload(sessionA2AndConnection.first)
                    val joinerBroadcast = receivePayload(sessionA2AndConnection.first)
                    val memberBroadcast = receivePayload(sessionA1AndConnection.first)
                    val otherLobbyPayload = receivePayloadOrNull(sessionB1AndConnection.first)

                    assertIs<JoinLobbyResponse>(joinerResponse)
                    assertEquals(JoinLobbyResponse(lobbyA), joinerResponse)
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(1), "Alice", isHost = true),
                        joinerExistingMemberEvent,
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(2), "Bob"),
                        joinerBroadcast,
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(2), "Bob"),
                        memberBroadcast,
                    )
                    assertNull(otherLobbyPayload)

                    sessionA1AndConnection.first.close()
                    sessionA2AndConnection.first.close()
                    sessionB1AndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `successful leave sends response and lobby scoped event only to remaining members`() =
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
                )

            application {
                module(network)
            }

            val lobbyA = LobbyCode("EF56")
            val lobbyB = LobbyCode("GH78")
            lobbyManager.createLobby(lobbyA)
            lobbyManager.createLobby(lobbyB)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionA1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionA2AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(2),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionB1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(3),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionA1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Alice")),
                        ),
                    )
                    receivePayload(sessionA1AndConnection.first)
                    receivePayload(sessionA1AndConnection.first)
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyA,
                            activePlayerId = PlayerId(1),
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = PlayerId(1),
                        ),
                        receivePayload(sessionA1AndConnection.first),
                    )

                    sessionA2AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Bob")),
                        ),
                    )
                    receivePayload(sessionA2AndConnection.first)
                    receivePayload(sessionA2AndConnection.first)
                    receivePayload(sessionA2AndConnection.first)
                    receivePayload(sessionA1AndConnection.first)

                    sessionB1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyB, "Carol")),
                        ),
                    )
                    receivePayload(sessionB1AndConnection.first)
                    receivePayload(sessionB1AndConnection.first)
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyB,
                            activePlayerId = PlayerId(3),
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = PlayerId(3),
                        ),
                        receivePayload(sessionB1AndConnection.first),
                    )

                    sessionA2AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(LeaveLobbyRequest(lobbyA)),
                        ),
                    )

                    waitUntilProcessed(lobbyManager, lobbyA, expectedCount = 3)

                    val leaverResponse = receivePayload(sessionA2AndConnection.first)
                    val remainingMemberEvent = receivePayload(sessionA1AndConnection.first)
                    val leaverScopedPayload = receivePayloadOrNull(sessionA2AndConnection.first)
                    val otherLobbyPayload = receivePayloadOrNull(sessionB1AndConnection.first)

                    assertEquals(LeaveLobbyResponse(lobbyA), leaverResponse)
                    assertEquals(
                        PlayerLeftLobbyEvent(lobbyA, PlayerId(2), newHost = PlayerId(1)),
                        remainingMemberEvent,
                    )
                    assertNull(leaverScopedPayload)
                    assertNull(otherLobbyPayload)
                    assertEquals(
                        listOf(PlayerId(1)),
                        lobbyManager.getLobby(lobbyA)?.currentState()?.players,
                    )

                    sessionA1AndConnection.first.close()
                    sessionA2AndConnection.first.close()
                    sessionB1AndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `create request creates lobby and returns create response to requesting client`() =
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
                    val sessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionAndConnection.first.send(
                        Frame.Binary(fin = true, data = MessageCodec.encode(CreateLobbyRequest)),
                    )

                    val payload = receivePayload(sessionAndConnection.first)
                    val response = assertIs<CreateLobbyResponse>(payload)
                    assertEquals(4, response.lobbyCode.value.length)
                    assertEquals(
                        response.lobbyCode,
                        lobbyManager.getLobby(response.lobbyCode)?.lobbyCode,
                    )

                    sessionAndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `full websocket lobby lifecycle create join kick leave and start game`() =
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
                )

            application {
                module(network)
            }

            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            val hostId = PlayerId(1)
            val joinerAId = PlayerId(2)
            val leavePlayerId = PlayerId(3)
            val kickedPlayerId = PlayerId(4)

            try {
                coroutineScope {
                    val hostSessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = hostId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val joinerASessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = joinerAId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val leavePlayerSessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = leavePlayerId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val kickedPlayerSessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = kickedPlayerId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    hostSessionAndConnection.first.send(
                        Frame.Binary(fin = true, data = MessageCodec.encode(CreateLobbyRequest)),
                    )
                    val createResponse =
                        assertIs<CreateLobbyResponse>(
                            receivePayload(hostSessionAndConnection.first),
                        )
                    val lobbyCode = createResponse.lobbyCode

                    // Der Lifecycle-Test setzt explizit Owner + Pre-Game-State fuer Kick/Start.
                    lobbyManager.removeLobby(lobbyCode)
                    lobbyManager.createLobby(
                        lobbyCode = lobbyCode,
                        initialState =
                            createPreGameState(
                                lobbyCode = lobbyCode,
                                ownerId = hostId,
                                players = listOf(hostId),
                                displayNames = mapOf(hostId to "Host"),
                            ),
                    )

                    joinerASessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "JoinerA")),
                        ),
                    )
                    assertEquals(
                        JoinLobbyResponse(lobbyCode),
                        receivePayload(joinerASessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, hostId, "Host", isHost = true),
                        receivePayload(joinerASessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, joinerAId, "JoinerA"),
                        receivePayload(joinerASessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, joinerAId, "JoinerA"),
                        receivePayload(hostSessionAndConnection.first),
                    )

                    leavePlayerSessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Leaver")),
                        ),
                    )
                    assertEquals(
                        JoinLobbyResponse(lobbyCode),
                        receivePayload(leavePlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, hostId, "Host", isHost = true),
                        receivePayload(leavePlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, joinerAId, "JoinerA"),
                        receivePayload(leavePlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, leavePlayerId, "Leaver"),
                        receivePayload(leavePlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, leavePlayerId, "Leaver"),
                        receivePayload(hostSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, leavePlayerId, "Leaver"),
                        receivePayload(joinerASessionAndConnection.first),
                    )

                    kickedPlayerSessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "KickMe")),
                        ),
                    )
                    assertEquals(
                        JoinLobbyResponse(lobbyCode),
                        receivePayload(kickedPlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, hostId, "Host", isHost = true),
                        receivePayload(kickedPlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, joinerAId, "JoinerA"),
                        receivePayload(kickedPlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, leavePlayerId, "Leaver"),
                        receivePayload(kickedPlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, kickedPlayerId, "KickMe"),
                        receivePayload(kickedPlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, kickedPlayerId, "KickMe"),
                        receivePayload(hostSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, kickedPlayerId, "KickMe"),
                        receivePayload(joinerASessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, kickedPlayerId, "KickMe"),
                        receivePayload(leavePlayerSessionAndConnection.first),
                    )

                    hostSessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    KickPlayerRequest(
                                        lobbyCode = lobbyCode,
                                        targetPlayerId = kickedPlayerId,
                                        requesterPlayerId = hostId,
                                    ),
                                ),
                        ),
                    )
                    assertEquals(
                        KickPlayerResponse(),
                        receivePayload(hostSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerKickedLobbyEvent(
                            lobbyCode = lobbyCode,
                            targetPlayerId = kickedPlayerId,
                            requesterPlayerId = hostId,
                        ),
                        receivePayload(hostSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerKickedLobbyEvent(
                            lobbyCode = lobbyCode,
                            targetPlayerId = kickedPlayerId,
                            requesterPlayerId = hostId,
                        ),
                        receivePayload(joinerASessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerKickedLobbyEvent(
                            lobbyCode = lobbyCode,
                            targetPlayerId = kickedPlayerId,
                            requesterPlayerId = hostId,
                        ),
                        receivePayload(leavePlayerSessionAndConnection.first),
                    )
                    assertNull(receivePayloadOrNull(kickedPlayerSessionAndConnection.first))

                    leavePlayerSessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(LeaveLobbyRequest(lobbyCode)),
                        ),
                    )
                    assertEquals(
                        LeaveLobbyResponse(lobbyCode),
                        receivePayload(leavePlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerLeftLobbyEvent(lobbyCode, leavePlayerId, newHost = hostId),
                        receivePayload(hostSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerLeftLobbyEvent(lobbyCode, leavePlayerId, newHost = hostId),
                        receivePayload(joinerASessionAndConnection.first),
                    )
                    assertNull(receivePayloadOrNull(leavePlayerSessionAndConnection.first))

                    waitUntilProcessed(lobbyManager, lobbyCode, expectedCount = 5)

                    lobbyManager.removeLobby(lobbyCode)
                    val offlineThirdPlayerId = PlayerId(999)
                    lobbyManager.createLobby(
                        lobbyCode = lobbyCode,
                        initialState =
                            createPreGameState(
                                lobbyCode = lobbyCode,
                                ownerId = hostId,
                                players = listOf(hostId, joinerAId, offlineThirdPlayerId),
                                displayNames =
                                    mapOf(
                                        hostId to "Host",
                                        joinerAId to "JoinerA",
                                        offlineThirdPlayerId to "OfflineThird",
                                    ),
                            ),
                    )

                    hostSessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(StartGameRequest(lobbyCode = lobbyCode)),
                        ),
                    )
                    assertEquals(
                        StartGameResponse(),
                        receivePayload(hostSessionAndConnection.first),
                    )
                    assertEquals(
                        GameStartedEvent(lobbyCode),
                        receivePayload(hostSessionAndConnection.first),
                    )
                    assertEquals(
                        GameStartedEvent(lobbyCode),
                        receivePayload(joinerASessionAndConnection.first),
                    )
                    assertNull(receivePayloadOrNull(leavePlayerSessionAndConnection.first))
                    assertNull(receivePayloadOrNull(kickedPlayerSessionAndConnection.first))

                    assertEquals(
                        listOf(hostId, joinerAId, offlineThirdPlayerId),
                        lobbyManager.getLobby(lobbyCode)?.currentState()?.players,
                    )

                    hostSessionAndConnection.first.close()
                    joinerASessionAndConnection.first.close()
                    leavePlayerSessionAndConnection.first.close()
                    kickedPlayerSessionAndConnection.first.close()
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
    ): NetworkMessagePayload {
        repeat(10) {
            val payload = receiveAnyPayload(session)
            if (payload !is GameStateDeltaEvent && payload !is GameStateSnapshotBroadcast) {
                return payload
            }
        }
        throw AssertionError("Expected non-delta payload within 10 messages.")
    }

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): NetworkMessagePayload? {
        repeat(5) {
            val frame =
                withTimeoutOrNull(200) {
                    session.incoming.receive()
                } ?: return null

            val binary = assertIs<Frame.Binary>(frame)
            val payload = MessageCodec.decodePayload(binary.readBytes())
            if (payload !is GameStateDeltaEvent && payload !is GameStateSnapshotBroadcast) {
                return payload
            }
        }
        return null
    }

    private suspend inline fun <reified T : NetworkMessagePayload> receivePayloadOfType(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
        maxMessages: Int = 5,
    ): T {
        repeat(maxMessages) {
            val payload = receiveAnyPayload(session)
            if (payload is T) {
                return payload
            }
        }
        throw AssertionError(
            "Expected payload of type ${T::class.java.simpleName} within $maxMessages messages.",
        )
    }

    private suspend fun receiveAnyPayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): NetworkMessagePayload {
        val frame =
            withTimeout(5_000) {
                session.incoming.receive()
            }

        val binary = assertIs<Frame.Binary>(frame)
        return MessageCodec.decodePayload(binary.readBytes())
    }

    private suspend fun waitUntilProcessed(
        manager: LobbyManager,
        lobbyCode: LobbyCode,
        expectedCount: Long,
    ) {
        withTimeout(5_000) {
            while (
                (manager.getLobby(lobbyCode)?.currentState()?.processedEventCount ?: 0L) <
                expectedCount
            ) {
                delay(5)
            }
        }
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T)
        return value as T
    }

    private fun createPreGameState(
        lobbyCode: LobbyCode,
        ownerId: PlayerId,
        players: List<PlayerId>,
        displayNames: Map<PlayerId, String>,
    ): GameState =
        GameState
            .initial(
                lobbyCode = lobbyCode,
                mapDefinition = defaultMapDefinition(),
                players = players,
                playerDisplayNames = displayNames,
            ).copy(
                lobbyOwner = ownerId,
                activePlayer = players.firstOrNull(),
                turnOrder = players,
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

    private fun createMappedGameState(
        lobbyCode: LobbyCode,
        playerId: PlayerId,
    ): GameState {
        val reducer = DefaultLobbyEventReducer()
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = defaultMapDefinition(),
                players = listOf(playerId),
                playerDisplayNames = mapOf(playerId to "Host"),
            )

        return reducer.apply(
            reducer.apply(
                reducer.apply(
                    baseState,
                    TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("argentinien"), playerId),
                ),
                TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("argentinien"), 5),
            ),
            TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("brasilien"), 2),
        )
    }

    private fun defaultMapDefinition() = MapConfigLoader.loadDefault()
}
