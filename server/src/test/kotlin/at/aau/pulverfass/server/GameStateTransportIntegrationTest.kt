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
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpReason
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
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
                    val playerOneSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
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
                            stateVersion = 0,
                            handCards = emptyList(),
                            secretObjectives = emptyList(),
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertNull(receivePayloadOrNull(playerTwoSession.first))

                    assertAdvanceSequence(
                        actor = playerOneSession.first,
                        watcher = playerTwoSession.first,
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
                        actor = playerOneSession.first,
                        watcher = playerTwoSession.first,
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
                        actor = playerOneSession.first,
                        watcher = playerTwoSession.first,
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
                        actor = playerOneSession.first,
                        watcher = playerTwoSession.first,
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
                        expectedGrantedEvent =
                            ReinforcementsGrantedEvent(
                                lobbyCode = lobbyCode,
                                playerId = playerTwo,
                                amount = 3,
                                territoryBonus = 3,
                                continentBonus = 0,
                                cardBonus = 0,
                            ),
                    )

                    playerTwoSession.first.send(
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
                            receiveAnyPayload(playerTwoSession.first),
                        )
                    assertEquals(lobbyCode, catchUp.lobbyCode)
                    assertEquals(5, catchUp.stateVersion)
                    assertEquals(playerTwo, catchUp.turnState.activePlayerId)
                    assertEquals(TurnPhase.REINFORCEMENTS, catchUp.turnState.turnPhase)
                    assertEquals(defaultMapDefinition().mapHash, catchUp.determinism.mapHash)
                    assertNull(receivePayloadOrNull(playerOneSession.first))

                    val history = routingService.roundHistory(lobbyCode)
                    assertEquals(1, history.size)
                    assertEquals(1, history.single().roundIndex)
                    assertEquals(5, history.single().endStateVersion)
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

                    playerOneSession.first.close()
                    playerTwoSession.first.close()
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
        expectedGrantedEvent: ReinforcementsGrantedEvent? = null,
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
        if (expectedGrantedEvent != null) {
            assertEquals(
                GameStateDeltaEvent(
                    lobbyCode = request.lobbyCode,
                    fromVersion = expectedVersion + 1,
                    toVersion = expectedVersion + 1,
                    events = listOf(expectedGrantedEvent),
                ),
                receiveAnyPayload(actor),
            )
        }
        assertEquals(TurnAdvanceResponse(request.lobbyCode), receiveAnyPayload(actor))
        assertEquals(
            PhaseBoundaryEvent(
                lobbyCode = request.lobbyCode,
                stateVersion =
                    if (expectedGrantedEvent == null) expectedVersion else expectedVersion + 1,
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
            assertEquals(
                if (expectedGrantedEvent == null) expectedVersion else expectedVersion + 1,
                snapshot.stateVersion,
            )
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
        if (expectedGrantedEvent != null) {
            assertEquals(
                GameStateDeltaEvent(
                    lobbyCode = request.lobbyCode,
                    fromVersion = expectedVersion + 1,
                    toVersion = expectedVersion + 1,
                    events = listOf(expectedGrantedEvent),
                ),
                receiveAnyPayload(watcher),
            )
        }
        assertEquals(
            PhaseBoundaryEvent(
                lobbyCode = request.lobbyCode,
                stateVersion =
                    if (expectedGrantedEvent == null) expectedVersion else expectedVersion + 1,
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
            assertEquals(
                if (expectedGrantedEvent == null) expectedVersion else expectedVersion + 1,
                snapshot.stateVersion,
            )
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
