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
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostReason
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
import at.aau.pulverfass.shared.network.codec.MessageCodec
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
                    waitingGame(
                        lobbyCode = lobbyCode,
                        players = listOf(hostId, playerTwo, playerThree),
                        hostId = hostId,
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
                    assertEquals(
                        GameStartedEvent(lobbyCode),
                        receivePayload(playerTwoSession.first),
                    )
                    assertEquals(configuredSetupEvent, receivePayload(playerTwoSession.first))
                    assertEquals(
                        GameStartedEvent(lobbyCode),
                        receivePayload(playerThreeSession.first),
                    )
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
                    val permissionError =
                        assertIs<TurnAdvanceErrorResponse>(receivePayload(hostSession.first))
                    assertEquals(TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER, permissionError.code)
                    assertNull(receivePayloadOrNull(playerTwoSession.first))
                    assertNull(receivePayloadOrNull(playerThreeSession.first))

                    val sessionsByPlayer =
                        mapOf(
                            hostId to hostSession.first,
                            playerTwo to playerTwoSession.first,
                            playerThree to playerThreeSession.first,
                        )
                    val runningTurnOrder =
                        lobbyManager
                            .getLobby(lobbyCode)
                            ?.currentState()
                            ?.turnOrder
                            ?: error("TurnOrder muss nach Spielstart gesetzt sein.")
                    val secondTurnPlayer = nextPlayerAfter(playerTwo, runningTurnOrder)
                    val thirdTurnPlayer = nextPlayerAfter(secondTurnPlayer, runningTurnOrder)

                    advancePlayerThroughFullTurn(
                        lobbyCode = lobbyCode,
                        activePlayer = playerTwo,
                        startPlayer = playerTwo,
                        currentTurnCount = 1,
                        nextPlayer = secondTurnPlayer,
                        nextTurnCount = 1,
                        sessionsByPlayer = sessionsByPlayer,
                    )
                    advancePlayerThroughFullTurn(
                        lobbyCode = lobbyCode,
                        activePlayer = secondTurnPlayer,
                        startPlayer = playerTwo,
                        currentTurnCount = 1,
                        nextPlayer = thirdTurnPlayer,
                        nextTurnCount = 1,
                        sessionsByPlayer = sessionsByPlayer,
                    )
                    advancePlayerThroughFullTurn(
                        lobbyCode = lobbyCode,
                        activePlayer = thirdTurnPlayer,
                        startPlayer = playerTwo,
                        currentTurnCount = 1,
                        nextPlayer = playerTwo,
                        nextTurnCount = 2,
                        sessionsByPlayer = sessionsByPlayer,
                    )

                    sessionsByPlayer.getValue(thirdTurnPlayer).send(
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
                        receivePayload(sessionsByPlayer.getValue(thirdTurnPlayer)),
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
                    waitingGame(
                        lobbyCode = lobbyCode,
                        players = listOf(hostId, playerTwo, playerThree),
                        hostId = hostId,
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
                    assertEquals(
                        GameStartedEvent(lobbyCode),
                        receivePayload(playerTwoSession.first),
                    )
                    assertEquals(initialTurnEvent, receivePayload(playerTwoSession.first))
                    assertEquals(
                        GameStartedEvent(lobbyCode),
                        receivePayload(playerThreeSession.first),
                    )
                    assertEquals(initialTurnEvent, receivePayload(playerThreeSession.first))

                    val sessionsByPlayer =
                        mapOf(
                            hostId to hostSession.first,
                            playerTwo to playerTwoSession.first,
                            playerThree to playerThreeSession.first,
                        )
                    val connectionsByKnownPlayer =
                        mapOf(
                            hostId to hostSession.second,
                            playerTwo to playerTwoSession.second,
                            playerThree to playerThreeSession.second,
                        )
                    val runningTurnOrder =
                        lobbyManager
                            .getLobby(lobbyCode)
                            ?.currentState()
                            ?.turnOrder
                            ?: error("TurnOrder muss nach Spielstart gesetzt sein.")
                    val disconnectedNextPlayer = nextPlayerAfter(hostId, runningTurnOrder)
                    val connectedWatcherSession =
                        sessionsByPlayer
                            .filterKeys { playerId ->
                                playerId != hostId &&
                                    playerId != disconnectedNextPlayer
                            }
                            .values
                            .single()

                    disconnectPlayer(
                        playerId = disconnectedNextPlayer,
                        session = sessionsByPlayer.getValue(disconnectedNextPlayer),
                        connectionId = connectionsByKnownPlayer.getValue(disconnectedNextPlayer),
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                        routingService = routingService,
                    )
                    assertEquals(
                        PlayerConnectionLostEvent(
                            lobbyCode = lobbyCode,
                            playerId = disconnectedNextPlayer,
                            reason = PlayerConnectionLostReason.SOCKET_CLOSED,
                        ),
                        receivePayload(hostSession.first),
                    )
                    assertEquals(
                        PlayerConnectionLostEvent(
                            lobbyCode = lobbyCode,
                            playerId = disconnectedNextPlayer,
                            reason = PlayerConnectionLostReason.SOCKET_CLOSED,
                        ),
                        receivePayload(connectedWatcherSession),
                    )

                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(connectedWatcherSession),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.REINFORCEMENTS),
                        expectedUpdate =
                            TurnStateUpdatedEvent(lobbyCode, hostId, TurnPhase.ATTACK, 1, hostId),
                    )
                    val attackAutoEnded =
                        consumeOptionalAttackAutoEnd(
                            actor = hostSession.first,
                            watchers = listOf(connectedWatcherSession),
                            lobbyCode = lobbyCode,
                            activePlayer = hostId,
                            turnCount = 1,
                            startPlayerId = hostId,
                        )
                    if (!attackAutoEnded) {
                        advanceAndAssertBroadcast(
                            actor = hostSession.first,
                            watchers = listOf(connectedWatcherSession),
                            request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.ATTACK),
                            expectedUpdate =
                                TurnStateUpdatedEvent(
                                    lobbyCode,
                                    hostId,
                                    TurnPhase.FORTIFY,
                                    1,
                                    hostId,
                                ),
                        )
                    }
                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(connectedWatcherSession),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.FORTIFY),
                        expectedUpdate =
                            TurnStateUpdatedEvent(
                                lobbyCode,
                                hostId,
                                TurnPhase.DRAW_CARD,
                                1,
                                hostId,
                            ),
                    )
                    advanceAndAssertBroadcast(
                        actor = hostSession.first,
                        watchers = listOf(connectedWatcherSession),
                        request = TurnAdvanceRequest(lobbyCode, hostId, TurnPhase.DRAW_CARD),
                        expectedUpdate =
                            TurnStateUpdatedEvent(
                                lobbyCode = lobbyCode,
                                activePlayerId = disconnectedNextPlayer,
                                turnPhase = TurnPhase.REINFORCEMENTS,
                                turnCount = 1,
                                startPlayerId = hostId,
                                isPaused = true,
                                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                                pausedPlayerId = disconnectedNextPlayer,
                            ),
                    )

                    val reconnectedNextPlayer =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = disconnectedNextPlayer,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    reconnectedNextPlayer.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = disconnectedNextPlayer,
                                        expectedPhase = TurnPhase.REINFORCEMENTS,
                                    ),
                                ),
                        ),
                    )
                    val pausedError =
                        assertIs<TurnAdvanceErrorResponse>(
                            receivePayload(reconnectedNextPlayer.first),
                        )
                    assertEquals(TurnAdvanceErrorCode.GAME_PAUSED, pausedError.code)

                    routingService.onPlayerConnected(disconnectedNextPlayer)

                    val resumedEvent =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = disconnectedNextPlayer,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = hostId,
                            isPaused = false,
                            pauseReason = null,
                            pausedPlayerId = null,
                        )
                    assertEquals(resumedEvent, receivePayload(hostSession.first))
                    assertEquals(resumedEvent, receivePayload(connectedWatcherSession))
                    assertEquals(resumedEvent, receivePayload(reconnectedNextPlayer.first))

                    reconnectedNextPlayer.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(TurnStateGetRequest(lobbyCode)),
                        ),
                    )
                    assertEquals(
                        TurnStateGetResponse(
                            lobbyCode = lobbyCode,
                            activePlayerId = disconnectedNextPlayer,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = hostId,
                        ),
                        receivePayload(reconnectedNextPlayer.first),
                    )

                    sessionsByPlayer
                        .filterKeys { playerId -> playerId != disconnectedNextPlayer }
                        .values
                        .forEach { session -> session.close() }
                    reconnectedNextPlayer.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private suspend fun advanceAndAssertBroadcast(
        actor: DefaultClientWebSocketSession,
        watchers: List<DefaultClientWebSocketSession>,
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
        assertPhaseBoundary(
            receivePayload(actor),
            request = request,
            expectedUpdate = expectedUpdate,
        )
        assertEquals(expectedUpdate, receivePayload(actor))
        watchers.forEach { watcher ->
            assertPhaseBoundary(
                receivePayload(watcher),
                request = request,
                expectedUpdate = expectedUpdate,
            )
            assertEquals(expectedUpdate, receivePayload(watcher))
        }
    }

    private suspend fun advancePlayerThroughFullTurn(
        lobbyCode: LobbyCode,
        activePlayer: PlayerId,
        startPlayer: PlayerId,
        currentTurnCount: Int,
        nextPlayer: PlayerId,
        nextTurnCount: Int,
        sessionsByPlayer: Map<PlayerId, DefaultClientWebSocketSession>,
    ) {
        val actor = sessionsByPlayer.getValue(activePlayer)
        val watchers = watcherSessions(sessionsByPlayer, activePlayer)
        advanceAndAssertBroadcast(
            actor = actor,
            watchers = watchers,
            request = TurnAdvanceRequest(lobbyCode, activePlayer, TurnPhase.REINFORCEMENTS),
            expectedUpdate =
                TurnStateUpdatedEvent(
                    lobbyCode,
                    activePlayer,
                    TurnPhase.ATTACK,
                    currentTurnCount,
                    startPlayer,
                ),
        )
        val attackAutoEnded =
            consumeOptionalAttackAutoEnd(
                actor = actor,
                watchers = watchers,
                lobbyCode = lobbyCode,
                activePlayer = activePlayer,
                turnCount = currentTurnCount,
                startPlayerId = startPlayer,
            )
        if (!attackAutoEnded) {
            advanceAndAssertBroadcast(
                actor = actor,
                watchers = watchers,
                request = TurnAdvanceRequest(lobbyCode, activePlayer, TurnPhase.ATTACK),
                expectedUpdate =
                    TurnStateUpdatedEvent(
                        lobbyCode,
                        activePlayer,
                        TurnPhase.FORTIFY,
                        currentTurnCount,
                        startPlayer,
                    ),
            )
        }
        advanceAndAssertBroadcast(
            actor = actor,
            watchers = watchers,
            request = TurnAdvanceRequest(lobbyCode, activePlayer, TurnPhase.FORTIFY),
            expectedUpdate =
                TurnStateUpdatedEvent(
                    lobbyCode,
                    activePlayer,
                    TurnPhase.DRAW_CARD,
                    currentTurnCount,
                    startPlayer,
                ),
        )
        advanceAndAssertBroadcast(
            actor = actor,
            watchers = watchers,
            request = TurnAdvanceRequest(lobbyCode, activePlayer, TurnPhase.DRAW_CARD),
            expectedUpdate =
                TurnStateUpdatedEvent(
                    lobbyCode,
                    nextPlayer,
                    TurnPhase.REINFORCEMENTS,
                    nextTurnCount,
                    startPlayer,
                ),
        )
    }

    private suspend fun consumeOptionalAttackAutoEnd(
        actor: DefaultClientWebSocketSession,
        watchers: List<DefaultClientWebSocketSession>,
        lobbyCode: LobbyCode,
        activePlayer: PlayerId,
        turnCount: Int,
        startPlayerId: PlayerId,
    ): Boolean {
        val boundary =
            receivePayloadOrNull(actor)
                ?: return false

        val expectedUpdate =
            TurnStateUpdatedEvent(
                lobbyCode = lobbyCode,
                activePlayerId = activePlayer,
                turnPhase = TurnPhase.FORTIFY,
                turnCount = turnCount,
                startPlayerId = startPlayerId,
            )
        assertPhaseBoundary(
            payload = boundary,
            request = TurnAdvanceRequest(lobbyCode, activePlayer, TurnPhase.ATTACK),
            expectedUpdate = expectedUpdate,
        )
        assertEquals(expectedUpdate, receivePayload(actor))
        watchers.forEach { watcher ->
            assertPhaseBoundary(
                payload = receivePayload(watcher),
                request = TurnAdvanceRequest(lobbyCode, activePlayer, TurnPhase.ATTACK),
                expectedUpdate = expectedUpdate,
            )
            assertEquals(expectedUpdate, receivePayload(watcher))
        }
        return true
    }

    private fun nextPlayerAfter(
        currentPlayerId: PlayerId,
        turnOrder: List<PlayerId>,
    ): PlayerId {
        val currentIndex = turnOrder.indexOf(currentPlayerId)
        require(currentIndex >= 0) {
            "Spieler '${currentPlayerId.value}' muss Teil der TurnOrder sein."
        }

        return (turnOrder.drop(currentIndex + 1) + turnOrder.take(currentIndex + 1))
            .firstOrNull()
            ?: currentPlayerId
    }

    private fun watcherSessions(
        sessionsByPlayer: Map<PlayerId, DefaultClientWebSocketSession>,
        activePlayer: PlayerId,
    ): List<DefaultClientWebSocketSession> =
        sessionsByPlayer
            .filterKeys { playerId -> playerId != activePlayer }
            .values
            .toList()

    private fun assertPhaseBoundary(
        payload: Any?,
        request: TurnAdvanceRequest,
        expectedUpdate: TurnStateUpdatedEvent,
    ) {
        val boundary = assertIs<PhaseBoundaryEvent>(payload)
        assertEquals(request.lobbyCode, boundary.lobbyCode)
        assertEquals(
            request.expectedPhase ?: error("expectedPhase is required for boundary assertions."),
            boundary.previousPhase,
        )
        assertEquals(expectedUpdate.turnPhase, boundary.nextPhase)
        assertEquals(expectedUpdate.activePlayerId, boundary.activePlayerId)
        assertEquals(expectedUpdate.turnCount, boundary.turnCount)
        assertTrue(boundary.stateVersion >= 1)
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
        routingService.onPlayerDisconnected(
            connectionId = connectionId,
            playerId = playerId,
            reason = "socket closed",
        )
        session.close()
    }

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

    private fun waitingGame(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        hostId: PlayerId,
    ): GameState =
        GameState
            .initial(
                lobbyCode = lobbyCode,
                mapDefinition = defaultMapDefinition(),
                players = players,
                playerDisplayNames =
                    mapOf(
                        hostId to "Host",
                        players[1] to "Player 2",
                        players[2] to "Player 3",
                    ),
            ).copy(
                lobbyOwner = hostId,
                status = GameStatus.WAITING_FOR_PLAYERS,
            )

    private fun defaultMapDefinition() = MapConfigLoader.loadDefault()
}
