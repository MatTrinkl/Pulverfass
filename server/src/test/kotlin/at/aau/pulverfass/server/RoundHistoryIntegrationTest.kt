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
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class RoundHistoryIntegrationTest {
    @Test
    fun `after three rounds only last two rounds remain in round history buffer`() =
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

            val lobbyCode = LobbyCode("RHIT")
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
                    val sessionsByPlayer =
                        mapOf(
                            playerOne to playerOneSession.first,
                            playerTwo to playerTwoSession.first,
                        )

                    repeat(16) {
                        val turnState = lobbyManager.getLobby(lobbyCode)?.currentState()?.turnState ?: error("turnState missing")
                        val actor = sessionsByPlayer.getValue(turnState.activePlayerId)
                        val watcher =
                            sessionsByPlayer
                                .filterKeys { it != turnState.activePlayerId }
                                .values
                                .single()
                        val turnChange = turnState.turnPhase == TurnPhase.DRAW_CARD

                        actor.send(
                            Frame.Binary(
                                fin = true,
                                data =
                                    MessageCodec.encode(
                                        TurnAdvanceRequest(
                                            lobbyCode = lobbyCode,
                                            playerId = turnState.activePlayerId,
                                            expectedPhase = turnState.turnPhase,
                                        ),
                                    ),
                            ),
                        )

                        assertTrue(receivePayload(actor) is GameStateDeltaEvent)
                        assertEquals(TurnAdvanceResponse(lobbyCode), receivePayload(actor))
                        assertTrue(receivePayload(actor) is PhaseBoundaryEvent)
                        assertTrue(
                            receivePayload(actor) is at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent,
                        )
                        if (turnChange) {
                            assertTrue(receivePayload(actor) is GameStateSnapshotBroadcast)
                        }

                        assertTrue(receivePayload(watcher) is GameStateDeltaEvent)
                        assertTrue(receivePayload(watcher) is PhaseBoundaryEvent)
                        assertTrue(
                            receivePayload(watcher) is at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent,
                        )
                        if (turnChange) {
                            assertTrue(receivePayload(watcher) is GameStateSnapshotBroadcast)
                        }
                    }

                    val history = routingService.roundHistory(lobbyCode)
                    assertEquals(listOf(2, 3), history.map { it.roundIndex })

                    val roundTwo = history[0]
                    assertTrue(roundTwo.deltas.isNotEmpty())
                    assertTrue(roundTwo.phaseBoundaries.isNotEmpty())
                    assertTrue(roundTwo.turnStateChanges.isNotEmpty())
                    assertTrue(roundTwo.snapshots.isNotEmpty())

                    val roundThree = history[1]
                    assertEquals(3, roundThree.roundIndex)
                    assertEquals(
                        listOf(RoundSnapshotTrigger.TURN_CHANGE_BROADCAST),
                        roundThree.snapshots.map { it.trigger },
                    )
                    assertTrue(roundThree.startStateVersion <= roundThree.endStateVersion)
                    assertTrue(routingService.describeRoundHistory(lobbyCode).contains("round=3"))

                    playerOneSession.first.close()
                    playerTwoSession.first.close()
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
            )

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

    private fun defaultMapDefinition() = at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault()
}
