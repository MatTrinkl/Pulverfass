package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.server.routing.RoundSnapshotTrigger
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
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.request.FortifyMoveRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpReason
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.response.FortifyMoveResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
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

class GameStateTransportIntegrationTest {
    @Test
    fun `router integrates delta boundary snapshot private and catch up flows end to end`() =
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

            val lobbyCode = LobbyCode("GTI1")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                    ),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val (playerOneSocket, _) =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val (playerTwoSocket, _) =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    playerOneSocket.send(
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
                            stateVersion = 0,
                            handCards = emptyList(),
                            secretObjectives = emptyList(),
                        ),
                        receiveAnyPayload(playerOneSocket),
                    )
                    assertNull(receivePayloadOrNull(playerTwoSocket))

                    assertAdvanceSequence(
                        actor = playerOneSocket,
                        watcher = playerTwoSocket,
                        request =
                            TurnAdvanceRequest(
                                lobbyCode = lobbyCode,
                                playerId = playerOne,
                                expectedPhase = TurnPhase.REINFORCEMENTS,
                            ),
                        expectedVersion = 1,
                        expectedUpdate =
                            TurnStateUpdatedEvent(
                                lobbyCode = lobbyCode,
                                activePlayerId = playerOne,
                                turnPhase = TurnPhase.ATTACK,
                                turnCount = 1,
                                startPlayerId = playerOne,
                            ),
                    )
                    assertAdvanceSequence(
                        actor = playerOneSocket,
                        watcher = playerTwoSocket,
                        request = TurnAdvanceRequest(lobbyCode, playerOne, TurnPhase.ATTACK),
                        expectedVersion = 2,
                        expectedUpdate =
                            TurnStateUpdatedEvent(
                                lobbyCode = lobbyCode,
                                activePlayerId = playerOne,
                                turnPhase = TurnPhase.FORTIFY,
                                turnCount = 1,
                                startPlayerId = playerOne,
                            ),
                    )
                    assertAdvanceSequence(
                        actor = playerOneSocket,
                        watcher = playerTwoSocket,
                        request = TurnAdvanceRequest(lobbyCode, playerOne, TurnPhase.FORTIFY),
                        expectedVersion = 3,
                        expectedUpdate =
                            TurnStateUpdatedEvent(
                                lobbyCode = lobbyCode,
                                activePlayerId = playerOne,
                                turnPhase = TurnPhase.DRAW_CARD,
                                turnCount = 1,
                                startPlayerId = playerOne,
                            ),
                    )
                    assertAdvanceSequence(
                        actor = playerOneSocket,
                        watcher = playerTwoSocket,
                        request = TurnAdvanceRequest(lobbyCode, playerOne, TurnPhase.DRAW_CARD),
                        expectedVersion = 4,
                        expectedUpdate =
                            TurnStateUpdatedEvent(
                                lobbyCode = lobbyCode,
                                activePlayerId = playerTwo,
                                turnPhase = TurnPhase.REINFORCEMENTS,
                                turnCount = 1,
                                startPlayerId = playerOne,
                            ),
                        expectSnapshot = true,
                    )

                    playerTwoSocket.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    GameStateCatchUpRequest(
                                        lobbyCode = lobbyCode,
                                        clientStateVersion = 0,
                                        reason = GameStateCatchUpReason.MISSING_DELTA,
                                    ),
                                ),
                        ),
                    )

                    val catchUp =
                        assertIs<GameStateCatchUpResponse>(
                            receiveAnyPayload(playerTwoSocket),
                        )
                    assertEquals(lobbyCode, catchUp.lobbyCode)
                    assertEquals(4, catchUp.stateVersion)
                    assertEquals(playerTwo, catchUp.turnState.activePlayerId)
                    assertEquals(TurnPhase.REINFORCEMENTS, catchUp.turnState.turnPhase)
                    assertEquals(defaultMapDefinition().mapHash, catchUp.determinism.mapHash)
                    assertNull(receivePayloadOrNull(playerOneSocket))

                    val history = routingService.roundHistory(lobbyCode)
                    assertEquals(1, history.size)
                    assertEquals(1, history.single().roundIndex)
                    assertEquals(4, history.single().endStateVersion)
                    assertTrue(history.single().deltas.isNotEmpty())
                    assertTrue(history.single().phaseBoundaries.isNotEmpty())
                    assertTrue(history.single().turnStateChanges.isNotEmpty())
                    assertTrue(
                        history.single().snapshots.any {
                            it.trigger == RoundSnapshotTrigger.TURN_CHANGE_BROADCAST
                        },
                    )
                    assertTrue(
                        history.single().snapshots.any {
                            it.trigger == RoundSnapshotTrigger.CATCH_UP_RESPONSE
                        },
                    )

                    playerOneSocket.close()
                    playerTwoSocket.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `transport flow keeps fortify delta and catch up consistent`() =
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

            val lobbyCode = LobbyCode("GTI2")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGameWithFortifySetup(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.ATTACK,
                    ),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val (playerOneSocket, _) =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val (playerTwoSocket, _) =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    assertAdvanceSequence(
                        actor = playerOneSocket,
                        watcher = playerTwoSocket,
                        request = TurnAdvanceRequest(lobbyCode, playerOne, TurnPhase.ATTACK),
                        expectedVersion = 7,
                        expectedUpdate =
                            TurnStateUpdatedEvent(
                                lobbyCode = lobbyCode,
                                activePlayerId = playerOne,
                                turnPhase = TurnPhase.FORTIFY,
                                turnCount = 1,
                                startPlayerId = playerOne,
                            ),
                    )

                    playerOneSocket.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    FortifyMoveRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        fromTerritoryId = TerritoryId("alaska"),
                                        toTerritoryId = TerritoryId("kanada"),
                                        troopCount = 2,
                                    ),
                                ),
                        ),
                    )

                    val expectedFortifyDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 8,
                            toVersion = 8,
                            events =
                                listOf(
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = TerritoryId("alaska"),
                                        troopCount = 2,
                                        stateVersion = 8,
                                    ),
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = TerritoryId("kanada"),
                                        troopCount = 3,
                                        stateVersion = 8,
                                    ),
                                ),
                        )
                    assertEquals(expectedFortifyDelta, receiveAnyPayload(playerOneSocket))
                    assertEquals(
                        FortifyMoveResponse(lobbyCode),
                        receiveAnyPayload(playerOneSocket),
                    )
                    assertEquals(expectedFortifyDelta, receiveAnyPayload(playerTwoSocket))

                    assertAdvanceSequence(
                        actor = playerOneSocket,
                        watcher = playerTwoSocket,
                        request = TurnAdvanceRequest(lobbyCode, playerOne, TurnPhase.FORTIFY),
                        expectedVersion = 10,
                        expectedUpdate =
                            TurnStateUpdatedEvent(
                                lobbyCode = lobbyCode,
                                activePlayerId = playerOne,
                                turnPhase = TurnPhase.DRAW_CARD,
                                turnCount = 1,
                                startPlayerId = playerOne,
                            ),
                    )
                    assertAdvanceSequence(
                        actor = playerOneSocket,
                        watcher = playerTwoSocket,
                        request = TurnAdvanceRequest(lobbyCode, playerOne, TurnPhase.DRAW_CARD),
                        expectedVersion = 11,
                        expectedUpdate =
                            TurnStateUpdatedEvent(
                                lobbyCode = lobbyCode,
                                activePlayerId = playerTwo,
                                turnPhase = TurnPhase.REINFORCEMENTS,
                                turnCount = 1,
                                startPlayerId = playerOne,
                            ),
                        expectSnapshot = true,
                    )

                    playerTwoSocket.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    GameStateCatchUpRequest(
                                        lobbyCode = lobbyCode,
                                        clientStateVersion = 0,
                                        reason = GameStateCatchUpReason.MISSING_DELTA,
                                    ),
                                ),
                        ),
                    )

                    val catchUp =
                        assertIs<GameStateCatchUpResponse>(
                            receiveAnyPayload(playerTwoSocket),
                        )
                    assertEquals(11, catchUp.stateVersion)
                    assertEquals(playerTwo, catchUp.turnState.activePlayerId)
                    assertEquals(TurnPhase.REINFORCEMENTS, catchUp.turnState.turnPhase)
                    assertEquals(
                        2,
                        catchUp.territoryStates.first {
                            it.territoryId == TerritoryId("alaska")
                        }.troopCount,
                    )
                    assertEquals(
                        3,
                        catchUp.territoryStates.first {
                            it.territoryId == TerritoryId("kanada")
                        }.troopCount,
                    )

                    val state =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("snapshot missing")
                    assertEquals(false, state.fortifyUsedThisTurn)

                    playerOneSocket.close()
                    playerTwoSocket.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private suspend fun assertAdvanceSequence(
        actor: DefaultClientWebSocketSession,
        watcher: DefaultClientWebSocketSession,
        request: TurnAdvanceRequest,
        expectedVersion: Long,
        expectedUpdate: TurnStateUpdatedEvent,
        expectSnapshot: Boolean = false,
    ) {
        actor.send(
            Frame.Binary(
                fin = true,
                data = MessageCodec.encode(request),
            ),
        )

        assertEquals(
            GameStateDeltaEvent(
                lobbyCode = request.lobbyCode,
                fromVersion = expectedVersion,
                toVersion = expectedVersion,
                events = listOf(expectedUpdate),
            ),
            receiveAnyPayload(actor),
        )
        assertEquals(TurnAdvanceResponse(request.lobbyCode), receiveAnyPayload(actor))
        assertEquals(
            PhaseBoundaryEvent(
                lobbyCode = request.lobbyCode,
                stateVersion = expectedVersion,
                previousPhase = request.expectedPhase ?: error("expectedPhase required in test"),
                nextPhase = expectedUpdate.turnPhase,
                activePlayerId = expectedUpdate.activePlayerId,
                turnCount = expectedUpdate.turnCount,
            ),
            receiveAnyPayload(actor),
        )
        assertEquals(expectedUpdate, receiveAnyPayload(actor))
        if (expectSnapshot) {
            val snapshot = assertIs<GameStateSnapshotBroadcast>(receiveAnyPayload(actor))
            assertEquals(expectedVersion, snapshot.stateVersion)
            assertEquals(expectedUpdate.activePlayerId, snapshot.turnState.activePlayerId)
            assertEquals(expectedUpdate.turnPhase, snapshot.turnState.turnPhase)
        }

        assertEquals(
            GameStateDeltaEvent(
                lobbyCode = request.lobbyCode,
                fromVersion = expectedVersion,
                toVersion = expectedVersion,
                events = listOf(expectedUpdate),
            ),
            receiveAnyPayload(watcher),
        )
        assertEquals(
            PhaseBoundaryEvent(
                lobbyCode = request.lobbyCode,
                stateVersion = expectedVersion,
                previousPhase = request.expectedPhase ?: error("expectedPhase required in test"),
                nextPhase = expectedUpdate.turnPhase,
                activePlayerId = expectedUpdate.activePlayerId,
                turnCount = expectedUpdate.turnCount,
            ),
            receiveAnyPayload(watcher),
        )
        assertEquals(expectedUpdate, receiveAnyPayload(watcher))
        if (expectSnapshot) {
            val snapshot = assertIs<GameStateSnapshotBroadcast>(receiveAnyPayload(watcher))
            assertEquals(expectedVersion, snapshot.stateVersion)
            assertEquals(expectedUpdate.activePlayerId, snapshot.turnState.activePlayerId)
            assertEquals(expectedUpdate.turnPhase, snapshot.turnState.turnPhase)
        }
    }

    private fun runningTurnStateGame(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        turnPhase: TurnPhase,
    ): GameState =
        GameState
            .initial(
                lobbyCode = lobbyCode,
                mapDefinition = defaultMapDefinition(),
                players = players,
                playerDisplayNames = players.associateWith { "Player ${it.value}" },
            ).copy(
                lobbyOwner = players.firstOrNull(),
                activePlayer = activePlayerId,
                turnOrder = players,
                turnNumber = 1,
                turnState =
                    TurnState(
                        activePlayerId = activePlayerId,
                        turnPhase = turnPhase,
                        turnCount = 1,
                        startPlayerId = players.first(),
                    ),
                status = GameStatus.RUNNING,
            )

    private fun runningTurnStateGameWithFortifySetup(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        turnPhase: TurnPhase,
    ): GameState {
        val reducer = DefaultLobbyEventReducer()
        val baseState =
            runningTurnStateGame(
                lobbyCode = lobbyCode,
                players = players,
                activePlayerId = activePlayerId,
                turnPhase = turnPhase,
            )
        val playerOne = players.first()

        return listOf(
            TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alaska"), playerOne),
            TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("kanada"), playerOne),
            TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("usa"), playerOne),
            TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("alaska"), 4),
            TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("kanada"), 1),
            TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("usa"), 2),
        ).fold(baseState) { state, event ->
            reducer.apply(state, event)
        }
    }

    private suspend fun connectSessionWithConnection(
        client: io.ktor.client.HttpClient,
        network: ServerNetwork,
        playerId: PlayerId,
        playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
        connectionsByPlayer: ConcurrentHashMap<PlayerId, ConnectionId>,
    ): Pair<DefaultClientWebSocketSession, ConnectionId> =
        coroutineScope {
            val session = client.webSocketSession("/ws")
            val sessionToken = receiveTestConnectionToken(session)
            val connectionId = awaitTestConnectionId(network, sessionToken)
            playersByConnection[connectionId] = playerId
            connectionsByPlayer[playerId] = connectionId
            session to connectionId
        }

    private suspend fun receiveAnyPayload(session: DefaultClientWebSocketSession): Any =
        receiveRelevantTestPayload(session)

    private suspend fun receivePayloadOrNull(session: DefaultClientWebSocketSession): Any? =
        receiveRelevantTestPayloadOrNull(session = session, timeoutMillis = 300)

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(
            value is T,
            "Expected ${T::class.simpleName}, but was ${value?.let { it::class.simpleName }}.",
        )
        return value as T
    }

    private fun defaultMapDefinition() = MapConfigLoader.loadDefault()
}
