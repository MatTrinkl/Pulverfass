package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.logging.ServerLoggerNames
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.server.session.PersistedReconnectSession
import at.aau.pulverfass.server.session.SessionContextPersistenceHooks
import at.aau.pulverfass.server.session.SessionContextRegistry
import at.aau.pulverfass.server.session.SessionReconnectContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectErrorCode
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.ConnectionStatusUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostReason
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
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.KickPlayerResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
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
                    assertEquals(2, response.schemaVersion)
                    assertEquals(defaultMapDefinition().mapHash, response.mapHash)
                    assertEquals(3, response.stateVersion)
                    assertEquals(24, response.definition.territories.size)
                    assertEquals(24, response.territoryStates.size)
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
    fun `map get request returns payload too large error when configured limit is exceeded`() =
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
                    publicStatePayloadMaxBytes = 128,
                )

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("MP13")
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
                    val error = assertIs<MapGetErrorResponse>(payload)

                    assertEquals(MapGetErrorCode.PAYLOAD_TOO_LARGE, error.code)
                    assertTrue(error.reason.contains("128 Bytes"))

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
                    assertTrue(createResponse.lobbyCode.value.all(Char::isDigit))

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
                            playerDisplayName = "ALICE",
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
                    assertEquals(2, response.schemaVersion)
                    assertEquals(defaultMapDefinition().mapHash, response.mapHash)
                    assertEquals(2, response.stateVersion)
                    assertEquals(24, response.definition.territories.size)
                    assertEquals(6, response.definition.continents.size)
                    assertEquals(24, response.territoryStates.size)
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
    fun `create lobby failure sends error response and reports routing error`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager =
                LobbyManager(
                    scope = serverScope,
                    initialStateFactory = {
                        throw IllegalStateException("Lobby creation disabled for test.")
                    },
                )
            val router =
                MainServerRouter(
                    lobbyManager = lobbyManager,
                    mapper = DefaultNetworkToLobbyEventMapper(),
                )
            val routingErrors = AtomicInteger(0)
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { null },
                    hooks =
                        MainServerLobbyRoutingServiceHooks(
                            onRoutingError = { _, _ -> routingErrors.incrementAndGet() },
                        ),
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
                    val session = client.webSocketSession("/ws")
                    try {
                        discardConnectionHandshake(session)
                        session.send(
                            Frame.Binary(
                                fin = true,
                                data = MessageCodec.encode(CreateLobbyRequest),
                            ),
                        )

                        val error = assertIs<CreateLobbyErrorResponse>(receivePayload(session))
                        assertEquals(
                            "Konnte keinen eindeutigen Lobby-Code erzeugen.",
                            error.reason,
                        )
                        waitUntilAtLeast(routingErrors, expectedCount = 1)
                    } finally {
                        session.close()
                    }
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
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
                    waitUntilAtLeast(routedPackets, expectedCount = 3)

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
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(1), "ALICE", isHost = true),
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
                        PlayerJoinedLobbyEvent(lobbyB, PlayerId(3), "CAROL", isHost = true),
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
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(1), "ALICE", isHost = true),
                        joinerExistingMemberEvent,
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(2), "BOB"),
                        joinerBroadcast,
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(2), "BOB"),
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
                    assertEquals(
                        1L,
                        lobbyManager
                            .getLobby(response.lobbyCode)
                            ?.currentState()
                            ?.processedEventCount,
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
                        PlayerJoinedLobbyEvent(lobbyCode, hostId, "HOST", isHost = true),
                        receivePayload(joinerASessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, joinerAId, "JOINERA"),
                        receivePayload(joinerASessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, joinerAId, "JOINERA"),
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
                        PlayerJoinedLobbyEvent(lobbyCode, hostId, "HOST", isHost = true),
                        receivePayload(leavePlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, joinerAId, "JOINERA"),
                        receivePayload(leavePlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, leavePlayerId, "LEAVER"),
                        receivePayload(leavePlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, leavePlayerId, "LEAVER"),
                        receivePayload(hostSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, leavePlayerId, "LEAVER"),
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
                        PlayerJoinedLobbyEvent(lobbyCode, hostId, "HOST", isHost = true),
                        receivePayload(kickedPlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, joinerAId, "JOINERA"),
                        receivePayload(kickedPlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, leavePlayerId, "LEAVER"),
                        receivePayload(kickedPlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, kickedPlayerId, "KICKME"),
                        receivePayload(kickedPlayerSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, kickedPlayerId, "KICKME"),
                        receivePayload(hostSessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, kickedPlayerId, "KICKME"),
                        receivePayload(joinerASessionAndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, kickedPlayerId, "KICKME"),
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

    @Test
    fun `reconnect restores lobby context and routes broadcasts to new connection`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router =
                MainServerRouter(
                    lobbyManager = lobbyManager,
                    mapper = DefaultNetworkToLobbyEventMapper(),
                )
            val sessionContextRegistry = SessionContextRegistry()
            network.installReconnectHooks(
                reconnectSessionProvider = { sessionToken ->
                    sessionContextRegistry.contextFor(sessionToken)?.let { context ->
                        PersistedReconnectSession(
                            context = context,
                            expiresAtEpochMillis = Long.MAX_VALUE,
                        )
                    }
                },
                onSessionRemoved = sessionContextRegistry::removeSession,
            )
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    sessionContextRegistry = sessionContextRegistry,
                    playerIdResolver = { connectionId ->
                        network.sessionManager
                            .getByConnectionId(connectionId)
                            ?.sessionToken
                            ?.let(sessionContextRegistry::playerIdForSession)
                    },
                    connectionIdResolver = { playerId ->
                        sessionContextRegistry
                            .sessionTokenForPlayer(playerId)
                            ?.let(network.sessionManager::getByToken)
                            ?.connectionId
                    },
                )

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("RJ42")
            lobbyManager.createLobby(lobbyCode)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val aliceSession = client.webSocketSession("/ws")
                    val aliceToken = discardConnectionHandshake(aliceSession)
                    val aliceConnectionId = awaitConnectionId(network, aliceToken)
                    sessionContextRegistry.assignPlayer(aliceToken, PlayerId(1))

                    aliceSession.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Alice")),
                        ),
                    )
                    assertEquals(JoinLobbyResponse(lobbyCode), receivePayload(aliceSession))
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, PlayerId(1), "ALICE", isHost = true),
                        receivePayload(aliceSession),
                    )

                    aliceSession.close()
                    awaitDetachedSession(network, aliceToken)

                    val reconnectingSession = client.webSocketSession("/ws")
                    discardConnectionHandshake(reconnectingSession)
                    reconnectingSession.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(ReconnectRequest(aliceToken)),
                        ),
                    )

                    val reconnectResponse =
                        assertIs<ReconnectResponse>(
                            receivePayload(reconnectingSession),
                        )
                    val reboundConnectionId = awaitConnectionId(network, aliceToken)

                    assertEquals(
                        ReconnectResponse(
                            success = true,
                            playerId = PlayerId(1),
                            lobbyCode = lobbyCode,
                            playerDisplayName = "ALICE",
                        ),
                        reconnectResponse,
                    )
                    assertTrue(aliceConnectionId != reboundConnectionId)
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, PlayerId(1), "ALICE", isHost = true),
                        receivePayload(reconnectingSession),
                    )
                    assertEquals(
                        ConnectionStatusUpdateEvent(
                            lobbyCode = lobbyCode,
                            playerId = PlayerId(1),
                            status = ConnectionStatus.CONNECTED,
                        ),
                        receivePayloadOfType<ConnectionStatusUpdateEvent>(reconnectingSession),
                    )

                    val bobSession = client.webSocketSession("/ws")
                    val bobToken = discardConnectionHandshake(bobSession)
                    sessionContextRegistry.assignPlayer(bobToken, PlayerId(2))
                    bobSession.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Bob")),
                        ),
                    )

                    assertEquals(JoinLobbyResponse(lobbyCode), receivePayload(bobSession))
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, PlayerId(1), "ALICE", isHost = true),
                        receivePayload(bobSession),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, PlayerId(2), "BOB"),
                        receivePayload(bobSession),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyCode, PlayerId(2), "BOB"),
                        receivePayload(reconnectingSession),
                    )

                    reconnectingSession.close()
                    bobSession.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `disconnect broadcasts player connection lost event only to affected lobby`() =
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
                val aliceSession = client.webSocketSession("/ws")
                discardConnectionHandshake(aliceSession)
                aliceSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(CreateLobbyRequest),
                    ),
                )
                val lobbyA =
                    assertIs<CreateLobbyResponse>(
                        receivePayload(aliceSession),
                    ).lobbyCode
                aliceSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Alice")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyA), receivePayload(aliceSession))
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyA, PlayerId(1), "ALICE", isHost = true),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(aliceSession),
                )

                val bobSession = client.webSocketSession("/ws")
                discardConnectionHandshake(bobSession)
                bobSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Bob")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyA), receivePayload(bobSession))
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyA, PlayerId(1), "ALICE", isHost = true),
                    receivePayload(bobSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyA, PlayerId(2), "BOB"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(bobSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyA, PlayerId(2), "BOB"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(aliceSession),
                )

                val carolSession = client.webSocketSession("/ws")
                discardConnectionHandshake(carolSession)
                carolSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(CreateLobbyRequest),
                    ),
                )
                val lobbyB =
                    assertIs<CreateLobbyResponse>(
                        receivePayload(carolSession),
                    ).lobbyCode
                carolSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyB, "Carol")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyB), receivePayload(carolSession))
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyB, PlayerId(3), "CAROL", isHost = true),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(carolSession),
                )
                receivePayloadOfType<TurnStateUpdatedEvent>(carolSession)

                bobSession.close()

                assertEquals(
                    PlayerConnectionLostEvent(
                        lobbyCode = lobbyA,
                        playerId = PlayerId(2),
                        reason = PlayerConnectionLostReason.SOCKET_CLOSED,
                    ),
                    receivePayloadOfType<PlayerConnectionLostEvent>(aliceSession),
                )
                assertEquals(
                    ConnectionStatusUpdateEvent(
                        lobbyCode = lobbyA,
                        playerId = PlayerId(2),
                        status = ConnectionStatus.DISCONNECTED,
                    ),
                    receivePayloadOfType<ConnectionStatusUpdateEvent>(aliceSession),
                )
                assertNull(receivePayloadOrNull(carolSession))

                val daveSession = client.webSocketSession("/ws")
                discardConnectionHandshake(daveSession)
                daveSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Dave")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyA), receivePayload(daveSession))
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyA, PlayerId(1), "ALICE", isHost = true),
                    receivePayload(daveSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyA, PlayerId(2), "BOB"),
                    receivePayload(daveSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyA, PlayerId(4), "DAVE"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(daveSession),
                )
                assertEquals(
                    ConnectionStatusUpdateEvent(
                        lobbyCode = lobbyA,
                        playerId = PlayerId(2),
                        status = ConnectionStatus.DISCONNECTED,
                    ),
                    receivePayloadOfType<ConnectionStatusUpdateEvent>(daveSession),
                )

                aliceSession.close()
                carolSession.close()
                daveSession.close()
            }
        }

    @Test
    fun `stale disconnect after reconnect does not broadcast connection lost or pause turn`() =
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
                val aliceSession = client.webSocketSession("/ws")
                val aliceToken = discardConnectionHandshake(aliceSession)
                aliceSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(CreateLobbyRequest),
                    ),
                )
                val lobbyCode =
                    assertIs<CreateLobbyResponse>(
                        receivePayload(aliceSession),
                    ).lobbyCode
                aliceSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Alice")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyCode), receivePayload(aliceSession))
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(1), "ALICE", isHost = true),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(aliceSession),
                )
                receivePayloadOfType<TurnStateUpdatedEvent>(aliceSession)

                val bobSession = client.webSocketSession("/ws")
                discardConnectionHandshake(bobSession)
                bobSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Bob")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyCode), receivePayload(bobSession))
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(1), "ALICE", isHost = true),
                    receivePayload(bobSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(2), "BOB"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(bobSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(2), "BOB"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(aliceSession),
                )

                val carolSession = client.webSocketSession("/ws")
                discardConnectionHandshake(carolSession)
                carolSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Carol")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyCode), receivePayload(carolSession))
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(1), "ALICE", isHost = true),
                    receivePayload(carolSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(2), "BOB"),
                    receivePayload(carolSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(3), "CAROL"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(carolSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(3), "CAROL"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(aliceSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(3), "CAROL"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(bobSession),
                )

                aliceSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(StartGameRequest(lobbyCode)),
                    ),
                )
                assertEquals(StartGameResponse(), receivePayload(aliceSession))
                assertEquals(GameStartedEvent(lobbyCode), receivePayload(aliceSession))
                assertEquals(
                    TurnStateUpdatedEvent(
                        lobbyCode = lobbyCode,
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                    ),
                    receivePayloadOfType<TurnStateUpdatedEvent>(aliceSession),
                )
                assertEquals(GameStartedEvent(lobbyCode), receivePayload(bobSession))
                assertEquals(
                    TurnStateUpdatedEvent(
                        lobbyCode = lobbyCode,
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                    ),
                    receivePayloadOfType<TurnStateUpdatedEvent>(bobSession),
                )
                assertEquals(GameStartedEvent(lobbyCode), receivePayload(carolSession))
                assertEquals(
                    TurnStateUpdatedEvent(
                        lobbyCode = lobbyCode,
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                    ),
                    receivePayloadOfType<TurnStateUpdatedEvent>(carolSession),
                )

                val reconnectingSession = client.webSocketSession("/ws")
                discardConnectionHandshake(reconnectingSession)
                reconnectingSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(aliceToken)),
                    ),
                )
                assertEquals(
                    ReconnectResponse(
                        success = true,
                        playerId = PlayerId(1),
                        lobbyCode = lobbyCode,
                        playerDisplayName = "ALICE",
                    ),
                    receivePayload(reconnectingSession),
                )
                receivePayloadOfType<PlayerJoinedLobbyEvent>(reconnectingSession)
                receivePayloadOfType<PlayerJoinedLobbyEvent>(reconnectingSession)

                withTimeout(5_000) {
                    aliceSession.closeReason.await()
                }

                val expectedConnectedStatus =
                    ConnectionStatusUpdateEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(1),
                        status = ConnectionStatus.CONNECTED,
                    )
                assertEquals(
                    expectedConnectedStatus,
                    receivePayloadOfType<ConnectionStatusUpdateEvent>(bobSession),
                )
                assertEquals(
                    expectedConnectedStatus,
                    receivePayloadOfType<ConnectionStatusUpdateEvent>(carolSession),
                )
                assertNull(receivePayloadOrNull(bobSession))
                assertNull(receivePayloadOrNull(carolSession))

                reconnectingSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(TurnStateGetRequest(lobbyCode)),
                    ),
                )
                assertEquals(
                    TurnStateGetResponse(
                        lobbyCode = lobbyCode,
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                    ),
                    receivePayloadOfType<TurnStateGetResponse>(reconnectingSession),
                )

                reconnectingSession.close()
                bobSession.close()
                carolSession.close()
            }
        }

    @Test
    fun `failed reconnect does not dispatch lobby snapshot`() =
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
                discardConnectionHandshake(session)
                session.send(
                    Frame.Binary(
                        fin = true,
                        data =
                            MessageCodec.encode(
                                ReconnectRequest(
                                    SessionToken("123e4567-e89b-12d3-a456-426614174999"),
                                ),
                            ),
                    ),
                )

                assertEquals(
                    ReconnectResponse(
                        success = false,
                        errorCode = ReconnectErrorCode.TOKEN_INVALID,
                    ),
                    receivePayload(session),
                )
                assertNull(receivePayloadOrNull(session))

                session.close()
            }
        }

    @Test
    fun `reconnect without lobby context does not dispatch lobby snapshot`() =
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
                val sessionToken = discardConnectionHandshake(session)

                session.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(sessionToken)),
                    ),
                )

                assertEquals(
                    ReconnectResponse(
                        success = true,
                        playerId = PlayerId(1),
                    ),
                    receivePayload(session),
                )
                assertNull(receivePayloadOrNull(session))

                session.close()
            }
        }

    @Test
    fun `reconnect snapshot is skipped when session context is missing`() =
        routeReconnectAndAssertSkipLogged(
            sessionContextRegistry = SessionContextRegistry(),
            expectedReason = "reason=context-missing",
        )

    @Test
    fun `reconnect snapshot is skipped when player is missing from session context`() =
        routeReconnectAndAssertSkipLogged(
            sessionContextRegistry =
                SessionContextRegistry(
                    SessionContextPersistenceHooks(
                        loadContext = {
                            SessionReconnectContext(
                                lobbyCode = LobbyCode("PM12"),
                                playerDisplayName = "Missing Player",
                            )
                        },
                    ),
                ),
            expectedReason = "reason=player-missing-in-context",
        )

    @Test
    fun `reconnect snapshot is skipped when lobby does not exist`() =
        routeReconnectAndAssertSkipLogged(
            sessionContextRegistry =
                SessionContextRegistry(
                    SessionContextPersistenceHooks(
                        loadContext = {
                            SessionReconnectContext(
                                playerId = PlayerId(1),
                                lobbyCode = LobbyCode("NF12"),
                                playerDisplayName = "Alice",
                            )
                        },
                    ),
                ),
            expectedReason = "reason=lobby-not-found",
        )

    @Test
    fun `reconnect snapshot is skipped when player is not part of lobby`() =
        routeReconnectAndAssertSkipLogged(
            sessionContextRegistry =
                SessionContextRegistry(
                    SessionContextPersistenceHooks(
                        loadContext = {
                            SessionReconnectContext(
                                playerId = PlayerId(1),
                                lobbyCode = LobbyCode("NL12"),
                                playerDisplayName = "Alice",
                            )
                        },
                    ),
                ),
            configureLobby = { lobbyManager ->
                val lobbyCode = LobbyCode("NL12")
                lobbyManager.createLobby(
                    lobbyCode = lobbyCode,
                    initialState =
                        GameState.initial(
                            lobbyCode = lobbyCode,
                            mapDefinition = defaultMapDefinition(),
                            players = listOf(PlayerId(2)),
                            playerDisplayNames = mapOf(PlayerId(2) to "Bob"),
                        ),
                )
            },
            expectedReason = "reason=player-not-in-lobby",
        )

    @Test
    fun `active player reconnect resumes paused turn`() =
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
                val aliceSession = client.webSocketSession("/ws")
                val aliceToken = discardConnectionHandshake(aliceSession)
                aliceSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(CreateLobbyRequest),
                    ),
                )
                val lobbyCode =
                    assertIs<CreateLobbyResponse>(
                        receivePayload(aliceSession),
                    ).lobbyCode
                aliceSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Alice")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyCode), receivePayload(aliceSession))
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(1), "ALICE", isHost = true),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(aliceSession),
                )
                receivePayloadOfType<TurnStateUpdatedEvent>(aliceSession)

                val bobSession = client.webSocketSession("/ws")
                discardConnectionHandshake(bobSession)
                bobSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Bob")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyCode), receivePayload(bobSession))
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(1), "ALICE", isHost = true),
                    receivePayload(bobSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(2), "BOB"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(bobSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(2), "BOB"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(aliceSession),
                )

                val carolSession = client.webSocketSession("/ws")
                discardConnectionHandshake(carolSession)
                carolSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Carol")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyCode), receivePayload(carolSession))
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(1), "ALICE", isHost = true),
                    receivePayload(carolSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(2), "BOB"),
                    receivePayload(carolSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(3), "CAROL"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(carolSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(3), "CAROL"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(aliceSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(lobbyCode, PlayerId(3), "CAROL"),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(bobSession),
                )

                aliceSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(StartGameRequest(lobbyCode)),
                    ),
                )
                assertEquals(StartGameResponse(), receivePayload(aliceSession))
                assertEquals(GameStartedEvent(lobbyCode), receivePayload(aliceSession))
                receivePayloadOfType<TurnStateUpdatedEvent>(aliceSession)
                assertEquals(GameStartedEvent(lobbyCode), receivePayload(bobSession))
                receivePayloadOfType<TurnStateUpdatedEvent>(bobSession)
                assertEquals(GameStartedEvent(lobbyCode), receivePayload(carolSession))
                receivePayloadOfType<TurnStateUpdatedEvent>(carolSession)

                aliceSession.close()
                awaitDetachedSession(network, aliceToken)

                val expectedPause =
                    TurnStateUpdatedEvent(
                        lobbyCode = lobbyCode,
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                        isPaused = true,
                        pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                        pausedPlayerId = PlayerId(1),
                    )
                assertEquals(
                    PlayerConnectionLostEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(1),
                        reason = PlayerConnectionLostReason.SOCKET_CLOSED,
                    ),
                    receivePayloadOfType<PlayerConnectionLostEvent>(bobSession),
                )
                assertEquals(
                    ConnectionStatusUpdateEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(1),
                        status = ConnectionStatus.DISCONNECTED,
                    ),
                    receivePayloadOfType<ConnectionStatusUpdateEvent>(bobSession),
                )
                assertEquals(
                    expectedPause,
                    receivePayloadOfType<TurnStateUpdatedEvent>(bobSession),
                )
                assertEquals(
                    PlayerConnectionLostEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(1),
                        reason = PlayerConnectionLostReason.SOCKET_CLOSED,
                    ),
                    receivePayloadOfType<PlayerConnectionLostEvent>(carolSession),
                )
                assertEquals(
                    ConnectionStatusUpdateEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(1),
                        status = ConnectionStatus.DISCONNECTED,
                    ),
                    receivePayloadOfType<ConnectionStatusUpdateEvent>(carolSession),
                )
                assertEquals(
                    expectedPause,
                    receivePayloadOfType<TurnStateUpdatedEvent>(carolSession),
                )

                val reconnectingSession = client.webSocketSession("/ws")
                discardConnectionHandshake(reconnectingSession)
                reconnectingSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(aliceToken)),
                    ),
                )
                assertEquals(
                    ReconnectResponse(
                        success = true,
                        playerId = PlayerId(1),
                        lobbyCode = lobbyCode,
                        playerDisplayName = "ALICE",
                    ),
                    receivePayload(reconnectingSession),
                )
                val expectedResume =
                    TurnStateUpdatedEvent(
                        lobbyCode = lobbyCode,
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                    )
                assertEquals(
                    expectedResume,
                    receivePayloadOfType<TurnStateUpdatedEvent>(
                        session = reconnectingSession,
                        maxMessages = 10,
                    ),
                )
                assertEquals(
                    expectedResume,
                    receivePayloadOfType<TurnStateUpdatedEvent>(bobSession),
                )
                assertEquals(
                    expectedResume,
                    receivePayloadOfType<TurnStateUpdatedEvent>(carolSession),
                )
                val expectedConnectedStatus =
                    ConnectionStatusUpdateEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(1),
                        status = ConnectionStatus.CONNECTED,
                    )
                assertEquals(
                    expectedConnectedStatus,
                    receivePayloadOfType<ConnectionStatusUpdateEvent>(reconnectingSession),
                )
                assertEquals(
                    expectedConnectedStatus,
                    receivePayloadOfType<ConnectionStatusUpdateEvent>(bobSession),
                )
                assertEquals(
                    expectedConnectedStatus,
                    receivePayloadOfType<ConnectionStatusUpdateEvent>(carolSession),
                )
                reconnectingSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(TurnStateGetRequest(lobbyCode)),
                    ),
                )
                assertEquals(
                    TurnStateGetResponse(
                        lobbyCode = lobbyCode,
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                    ),
                    receivePayloadOfType<TurnStateGetResponse>(reconnectingSession),
                )

                reconnectingSession.close()
                bobSession.close()
                carolSession.close()
            }
        }

    private suspend fun connectSessionWithConnection(
        client: io.ktor.client.HttpClient,
        network: ServerNetwork,
        playerId: PlayerId,
        playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
        connectionsByPlayer: ConcurrentHashMap<PlayerId, ConnectionId>,
    ) = coroutineScope {
        val session = client.webSocketSession("/ws")
        val sessionToken = discardConnectionHandshake(session)
        val connectionId = awaitConnectionId(network, sessionToken)
        playersByConnection[connectionId] = playerId
        connectionsByPlayer[playerId] = connectionId
        session to connectionId
    }

    private fun routeReconnectAndAssertSkipLogged(
        sessionContextRegistry: SessionContextRegistry,
        expectedReason: String,
        configureLobby: (LobbyManager) -> Unit = {},
    ) {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger =
            context.getLogger("${ServerLoggerNames.TECHNICAL}.MainServerLobbyRoutingService")
        val appender = ListAppender<ILoggingEvent>()
        appender.context = context
        appender.start()
        logger.addAppender(appender)

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
                sessionContextRegistry = sessionContextRegistry,
                playerIdResolver = { null },
            )

        try {
            configureLobby(lobbyManager)
            routingService.start(serverScope)

            val connectionId = ConnectionId(91)
            val sessionToken = network.sessionManager.createSession(connectionId).sessionToken

            kotlinx.coroutines.runBlocking {
                publishReconnectUntilLogContains(
                    network = network,
                    connectionId = connectionId,
                    sessionToken = sessionToken,
                    appender = appender,
                    expectedText = expectedReason,
                )
            }

            assertTrue(
                appender.list.any { event -> event.formattedMessage.contains(expectedReason) },
            )
        } finally {
            kotlinx.coroutines.runBlocking {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
            logger.detachAppender(appender)
        }
    }

    private suspend fun publishReconnectUntilLogContains(
        network: ServerNetwork,
        connectionId: ConnectionId,
        sessionToken: SessionToken,
        appender: ListAppender<ILoggingEvent>,
        expectedText: String,
    ) {
        withTimeout(5_000) {
            while (appender.list.none { event -> event.formattedMessage.contains(expectedText) }) {
                network.packetReceiver.decode(
                    connectionId = connectionId,
                    bytes = MessageCodec.encode(ReconnectRequest(sessionToken)),
                )
                delay(5)
            }
        }
    }

    private suspend fun receivePayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): NetworkMessagePayload = receiveRelevantTestPayload(session = session, skipGameSync = true)

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): NetworkMessagePayload? =
        receiveRelevantTestPayloadOrNull(
            session = session,
            skipGameSync = true,
            timeoutMillis = 200,
            maxMessages = 5,
        )

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
    ): NetworkMessagePayload = receiveRelevantTestPayload(session)

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

    private suspend fun waitUntilAtLeast(
        counter: AtomicInteger,
        expectedCount: Int,
    ) {
        withTimeout(5_000) {
            while (counter.get() < expectedCount) {
                delay(5)
            }
        }
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T)
        return value as T
    }

    private suspend fun discardConnectionHandshake(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): SessionToken {
        val payload = receiveRawTestPayload(session)
        val response = assertIs<ConnectionResponse>(payload)
        return response.sessionToken
    }

    private suspend fun awaitConnectionId(
        network: ServerNetwork,
        sessionToken: SessionToken,
    ): ConnectionId {
        return withTimeout(5_000) {
            var connectionId: ConnectionId? = null
            while (connectionId == null) {
                connectionId = network.sessionManager.getByToken(sessionToken)?.connectionId
                if (connectionId == null) {
                    delay(5)
                }
            }
            connectionId
        }
    }

    private suspend fun awaitDetachedSession(
        network: ServerNetwork,
        sessionToken: SessionToken,
    ) {
        withTimeout(5_000) {
            while (network.sessionManager.getByToken(sessionToken)?.connectionId != null) {
                delay(5)
            }
        }
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
