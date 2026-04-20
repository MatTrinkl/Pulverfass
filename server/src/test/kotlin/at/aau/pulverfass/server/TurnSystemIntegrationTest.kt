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
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
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

class TurnSystemIntegrationTest {
    @Test
    fun `turn system end to end wires setup start advance broadcasts and round increment`() =
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

            val lobbyCode = LobbyCode("TSI1")
            val hostId = PlayerId(1)
            val playerTwo = PlayerId(2)
            val playerThree = PlayerId(3)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    GameState.initial(
                        lobbyCode = lobbyCode,
                        mapDefinition = defaultMapDefinition(),
                        players = listOf(hostId, playerTwo, playerThree),
                        playerDisplayNames =
                            mapOf(
                                hostId to "Host",
                                playerTwo to "Player 2",
                                playerThree to "Player 3",
                            ),
                    ).copy(
                        lobbyOwner = hostId,
                        status = GameStatus.WAITING_FOR_PLAYERS,
                    ),
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
                            client = client,
                            network = network,
                            playerId = hostId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val playerTwoSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val playerThreeSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerThree,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    hostSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    StartPlayerSetRequest(
                                        lobbyCode = lobbyCode,
                                        startPlayerId = playerTwo,
                                        requesterPlayerId = hostId,
                                    ),
                                ),
                        ),
                    )

                    val configuredSetupEvent =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = playerTwo,
                        )
                    assertEquals(
                        StartPlayerSetResponse(lobbyCode = lobbyCode, startPlayerId = playerTwo),
                        receivePayload(hostSession.first),
                    )
                    assertEquals(configuredSetupEvent, receivePayload(hostSession.first))
                    assertEquals(configuredSetupEvent, receivePayload(playerTwoSession.first))
                    assertEquals(configuredSetupEvent, receivePayload(playerThreeSession.first))

                    hostSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(StartGameRequest(lobbyCode)),
                        ),
                    )

                    assertEquals(StartGameResponse(), receivePayload(hostSession.first))
                    assertEquals(GameStartedEvent(lobbyCode), receivePayload(hostSession.first))
                    assertEquals(configuredSetupEvent, receivePayload(hostSession.first))
                    assertEquals(GameStartedEvent(lobbyCode), receivePayload(playerTwoSession.first))
                    assertEquals(configuredSetupEvent, receivePayload(playerTwoSession.first))
                    assertEquals(GameStartedEvent(lobbyCode), receivePayload(playerThreeSession.first))
                    assertEquals(configuredSetupEvent, receivePayload(playerThreeSession.first))

                    hostSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = hostId,
                                        expectedPhase = TurnPhase.REINFORCEMENTS,
                                    ),
                                ),
                        ),
                    )
                    val permissionError = assertIs<TurnAdvanceErrorResponse>(receivePayload(hostSession.first))
                    assertEquals(TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER, permissionError.code)
                    assertNull(receivePayloadOrNull(playerTwoSession.first))
                    assertNull(receivePayloadOrNull(playerThreeSession.first))

                    advanceAndAssertBroadcast(
                        actor = playerTwoSession.first,
                        watchers = listOf(hostSession.first, playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, playerTwo, TurnPhase.REINFORCEMENTS),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, playerTwo, TurnPhase.ATTACK, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = playerTwoSession.first,
                        watchers = listOf(hostSession.first, playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, playerTwo, TurnPhase.ATTACK),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, playerTwo, TurnPhase.FORTIFY, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = playerTwoSession.first,
                        watchers = listOf(hostSession.first, playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, playerTwo, TurnPhase.FORTIFY),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, playerTwo, TurnPhase.DRAW_CARD, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = playerTwoSession.first,
                        watchers = listOf(hostSession.first, playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, playerTwo, TurnPhase.DRAW_CARD),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, playerThree, TurnPhase.REINFORCEMENTS, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = playerThreeSession.first,
                        watchers = listOf(hostSession.first, playerTwoSession.first),
                        request = TurnAdvanceRequest(lobbyCode, playerThree, TurnPhase.REINFORCEMENTS),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, playerThree, TurnPhase.ATTACK, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = playerThreeSession.first,
                        watchers = listOf(hostSession.first, playerTwoSession.first),
                        request = TurnAdvanceRequest(lobbyCode, playerThree, TurnPhase.ATTACK),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, playerThree, TurnPhase.FORTIFY, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = playerThreeSession.first,
                        watchers = listOf(hostSession.first, playerTwoSession.first),
                        request = TurnAdvanceRequest(lobbyCode, playerThree, TurnPhase.FORTIFY),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, playerThree, TurnPhase.DRAW_CARD, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = playerThreeSession.first,
                        watchers = listOf(hostSession.first, playerTwoSession.first),
                        request = TurnAdvanceRequest(lobbyCode, playerThree, TurnPhase.DRAW_CARD),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, hostId, TurnPhase.REINFORCEMENTS, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(playerTwoSession.first, playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.REINFORCEMENTS),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, hostId, TurnPhase.ATTACK, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(playerTwoSession.first, playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.ATTACK),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, hostId, TurnPhase.FORTIFY, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(playerTwoSession.first, playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.FORTIFY),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, hostId, TurnPhase.DRAW_CARD, 1, playerTwo),
                    )
                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(playerTwoSession.first, playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.DRAW_CARD),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, playerTwo, TurnPhase.REINFORCEMENTS, 2, playerTwo),
                    )

                    playerThreeSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(TurnStateGetRequest(lobbyCode)),
                        ),
                    )
                    assertEquals(
                        TurnStateGetResponse(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 2,
                            startPlayerId = playerTwo,
                        ),
                        receivePayload(playerThreeSession.first),
                    )
                    assertEquals(
                        2,
                        lobbyManager.getLobby(lobbyCode)?.currentState()?.turnState?.turnCount,
                    )

                    hostSession.first.close()
                    playerTwoSession.first.close()
                    playerThreeSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `turn system end to end pauses on disconnected next player and resumes after reconnect`() =
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

            val lobbyCode = LobbyCode("TSI2")
            val hostId = PlayerId(1)
            val playerTwo = PlayerId(2)
            val playerThree = PlayerId(3)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    GameState.initial(
                        lobbyCode = lobbyCode,
                        mapDefinition = defaultMapDefinition(),
                        players = listOf(hostId, playerTwo, playerThree),
                        playerDisplayNames =
                            mapOf(
                                hostId to "Host",
                                playerTwo to "Player 2",
                                playerThree to "Player 3",
                            ),
                    ).copy(
                        lobbyOwner = hostId,
                        status = GameStatus.WAITING_FOR_PLAYERS,
                    ),
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
                            client = client,
                            network = network,
                            playerId = hostId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val playerTwoSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val playerThreeSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerThree,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    hostSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(StartGameRequest(lobbyCode)),
                        ),
                    )
                    val initialTurnEvent =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = hostId,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = hostId,
                        )
                    assertEquals(StartGameResponse(), receivePayload(hostSession.first))
                    assertEquals(GameStartedEvent(lobbyCode), receivePayload(hostSession.first))
                    assertEquals(initialTurnEvent, receivePayload(hostSession.first))
                    assertEquals(GameStartedEvent(lobbyCode), receivePayload(playerTwoSession.first))
                    assertEquals(initialTurnEvent, receivePayload(playerTwoSession.first))
                    assertEquals(GameStartedEvent(lobbyCode), receivePayload(playerThreeSession.first))
                    assertEquals(initialTurnEvent, receivePayload(playerThreeSession.first))

                    disconnectPlayer(
                        playerId = playerTwo,
                        session = playerTwoSession.first,
                        connectionId = playerTwoSession.second,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                        routingService = routingService,
                    )

                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.REINFORCEMENTS),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, hostId, TurnPhase.ATTACK, 1, hostId),
                    )
                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.ATTACK),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, hostId, TurnPhase.FORTIFY, 1, hostId),
                    )
                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.FORTIFY),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, hostId, TurnPhase.DRAW_CARD, 1, hostId),
                    )
                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(playerThreeSession.first),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.DRAW_CARD),
                        expectedUpdate =
                            TurnStateUpdatedEvent(
                                lobbyCode = lobbyCode,
                                activePlayerId = playerTwo,
                                turnPhase = TurnPhase.REINFORCEMENTS,
                                turnCount = 1,
                                startPlayerId = hostId,
                                isPaused = true,
                                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                                pausedPlayerId = playerTwo,
                            ),
                    )

                    val reconnectedPlayerTwo =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    reconnectedPlayerTwo.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerTwo,
                                        expectedPhase = TurnPhase.REINFORCEMENTS,
                                    ),
                                ),
                        ),
                    )
                    val pausedError = assertIs<TurnAdvanceErrorResponse>(receivePayload(reconnectedPlayerTwo.first))
                    assertEquals(TurnAdvanceErrorCode.GAME_PAUSED, pausedError.code)

                    routingService.onPlayerConnected(playerTwo)

                    val resumedEvent =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = hostId,
                            isPaused = false,
                            pauseReason = null,
                            pausedPlayerId = null,
                        )
                    assertEquals(resumedEvent, receivePayload(hostSession.first))
                    assertEquals(resumedEvent, receivePayload(playerThreeSession.first))
                    assertEquals(resumedEvent, receivePayload(reconnectedPlayerTwo.first))

                    reconnectedPlayerTwo.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(TurnStateGetRequest(lobbyCode)),
                        ),
                    )
                    assertEquals(
                        TurnStateGetResponse(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = hostId,
                        ),
                        receivePayload(reconnectedPlayerTwo.first),
                    )

                    hostSession.first.close()
                    playerThreeSession.first.close()
                    reconnectedPlayerTwo.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private suspend fun advanceAndAssertBroadcast(
        actor: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
        watchers: List<io.ktor.client.plugins.websocket.DefaultClientWebSocketSession>,
        request: TurnAdvanceRequest,
        expectedUpdate: TurnStateUpdatedEvent,
    ) {
        actor.send(
            Frame.Binary(
                fin = true,
                data = MessageCodec.encode(request),
            ),
        )

        assertEquals(TurnAdvanceResponse(request.lobbyCode), receivePayload(actor))
        assertEquals(expectedUpdate, receivePayload(actor))
        watchers.forEach { watcher ->
            assertEquals(expectedUpdate, receivePayload(watcher))
        }
    }

    private suspend fun disconnectPlayer(
        playerId: PlayerId,
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
        connectionId: ConnectionId,
        playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
        connectionsByPlayer: ConcurrentHashMap<PlayerId, ConnectionId>,
        routingService: MainServerLobbyRoutingService,
    ) {
        playersByConnection.remove(connectionId)
        connectionsByPlayer.remove(playerId)
        routingService.onPlayerDisconnected(playerId)
        session.close()
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

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T, "Expected ${T::class.simpleName}, but was ${value?.let { it::class.simpleName }}.")
        return value as T
    }

    private fun defaultMapDefinition() = MapConfigLoader.loadDefault()
}
