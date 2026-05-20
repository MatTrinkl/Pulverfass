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
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.PendingReinforcements
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequest
import at.aau.pulverfass.shared.message.lobby.request.TerritoryPlacement
import at.aau.pulverfass.shared.message.lobby.response.PlaceReinforcementsResponse
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorResponse
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.ApplicationTestBuilder
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
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class PlaceReinforcementsIntegrationTest {
    @Test
    fun `valid batch placement updates troops and pending deterministically`() =
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

            val lobbyCode = LobbyCode("PRI1")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val territoryA = defaultMapDefinition().territories[0].territoryId
            val territoryB = defaultMapDefinition().territories[1].territoryId
            val territoryC = defaultMapDefinition().territories[2].territoryId

            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    reinforcementGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        pendingPlayerId = playerOne,
                        pendingAmount = 5,
                        owners =
                            mapOf(
                                territoryA to playerOne,
                                territoryB to playerOne,
                                territoryC to playerTwo,
                            ),
                        troopCounts =
                            mapOf(
                                territoryA to 1,
                                territoryB to 2,
                                territoryC to 4,
                            ),
                    ),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val actorSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val watcherSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    actorSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    PlaceReinforcementsRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        placements =
                                            listOf(
                                                TerritoryPlacement(territoryA, 2),
                                                TerritoryPlacement(territoryB, 2),
                                            ),
                                    ),
                                ),
                        ),
                    )

                    val actorTroopDeltaOne =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = territoryA,
                                        troopCount = 3,
                                        stateVersion = 1,
                                    ),
                                ),
                        )
                    val actorTroopEventOne =
                        TerritoryTroopsChangedEvent(
                            lobbyCode = lobbyCode,
                            territoryId = territoryA,
                            troopCount = 3,
                            stateVersion = 1,
                        )
                    val actorTroopDeltaTwo =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 2,
                            toVersion = 2,
                            events =
                                listOf(
                                    TerritoryTroopsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        territoryId = territoryB,
                                        troopCount = 4,
                                        stateVersion = 2,
                                    ),
                                ),
                        )
                    val actorTroopEventTwo =
                        TerritoryTroopsChangedEvent(
                            lobbyCode = lobbyCode,
                            territoryId = territoryB,
                            troopCount = 4,
                            stateVersion = 2,
                        )
                    val pendingDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 3,
                            toVersion = 3,
                            events =
                                listOf(
                                    PendingReinforcementsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        delta = -4,
                                    ),
                                ),
                        )

                    assertEquals(actorTroopDeltaOne, receiveAnyPayload(actorSession.first))
                    assertEquals(actorTroopEventOne, receiveAnyPayload(actorSession.first))
                    assertEquals(actorTroopDeltaTwo, receiveAnyPayload(actorSession.first))
                    assertEquals(actorTroopEventTwo, receiveAnyPayload(actorSession.first))
                    assertEquals(pendingDelta, receiveAnyPayload(actorSession.first))
                    assertEquals(
                        PlaceReinforcementsResponse(lobbyCode),
                        receiveAnyPayload(actorSession.first),
                    )

                    assertEquals(actorTroopDeltaOne, receiveAnyPayload(watcherSession.first))
                    assertEquals(actorTroopEventOne, receiveAnyPayload(watcherSession.first))
                    assertEquals(actorTroopDeltaTwo, receiveAnyPayload(watcherSession.first))
                    assertEquals(actorTroopEventTwo, receiveAnyPayload(watcherSession.first))
                    assertEquals(pendingDelta, receiveAnyPayload(watcherSession.first))

                    val updatedState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(1, updatedState.pendingReinforcementsFor(playerOne))
                    assertEquals(3, updatedState.troopCountOf(territoryA))
                    assertEquals(4, updatedState.troopCountOf(territoryB))
                    assertNull(receiveRelevantTestPayloadOrNull(actorSession.first))
                    assertNull(receiveRelevantTestPayloadOrNull(watcherSession.first))

                    actorSession.first.close()
                    watcherSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `invalid request is rejected when requester does not own all territories`() =
        testApplication {
            val lobbyCode = LobbyCode("PRI2")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val territoryA = defaultMapDefinition().territories[0].territoryId
            val territoryC = defaultMapDefinition().territories[2].territoryId
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 5,
                    owners = mapOf(territoryA to playerOne, territoryC to playerTwo),
                    troopCounts = mapOf(territoryA to 1, territoryC to 4),
                )

            val (error, snapshot) =
                exerciseFailingPlacement(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        PlaceReinforcementsRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                            placements = listOf(TerritoryPlacement(territoryC, 1)),
                        ),
                )

            assertEquals(PlaceReinforcementsErrorCode.TERRITORY_NOT_OWNED, error.code)
            assertEquals(baseState.stateVersion, snapshot.stateVersion)
            assertEquals(baseState.pendingReinforcementsFor(playerOne), snapshot.pendingReinforcementsFor(playerOne))
        }

    @Test
    fun `invalid request is rejected when requester is not active player`() =
        testApplication {
            val lobbyCode = LobbyCode("PRI3")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val territoryA = defaultMapDefinition().territories[0].territoryId
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerTwo,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerTwo,
                    pendingAmount = 5,
                    owners = mapOf(territoryA to playerOne),
                    troopCounts = mapOf(territoryA to 1),
                )

            val (error, snapshot) =
                exerciseFailingPlacement(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        PlaceReinforcementsRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                            placements = listOf(TerritoryPlacement(territoryA, 1)),
                        ),
                )

            assertEquals(PlaceReinforcementsErrorCode.NOT_ACTIVE_PLAYER, error.code)
            assertEquals(baseState.stateVersion, snapshot.stateVersion)
        }

    @Test
    fun `invalid request is rejected when phase is not reinforcements`() =
        testApplication {
            val lobbyCode = LobbyCode("PRI4")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val territoryA = defaultMapDefinition().territories[0].territoryId
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.ATTACK,
                    pendingPlayerId = playerOne,
                    pendingAmount = 5,
                    owners = mapOf(territoryA to playerOne),
                    troopCounts = mapOf(territoryA to 1),
                )

            val (error, snapshot) =
                exerciseFailingPlacement(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        PlaceReinforcementsRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                            placements = listOf(TerritoryPlacement(territoryA, 1)),
                        ),
                )

            assertEquals(PlaceReinforcementsErrorCode.PHASE_MISMATCH, error.code)
            assertEquals(baseState.stateVersion, snapshot.stateVersion)
        }

    @Test
    fun `invalid request is rejected when placements overspend pending pool`() =
        testApplication {
            val lobbyCode = LobbyCode("PRI5")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val territoryA = defaultMapDefinition().territories[0].territoryId
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                    owners = mapOf(territoryA to playerOne),
                    troopCounts = mapOf(territoryA to 1),
                )

            val (error, snapshot) =
                exerciseFailingPlacement(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        PlaceReinforcementsRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                            placements = listOf(TerritoryPlacement(territoryA, 3)),
                        ),
                )

            assertEquals(PlaceReinforcementsErrorCode.OVERSPEND, error.code)
            assertEquals(2, snapshot.pendingReinforcementsFor(playerOne))
            assertEquals(1, snapshot.troopCountOf(territoryA))
        }

    private suspend fun ApplicationTestBuilder.exerciseFailingPlacement(
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: PlaceReinforcementsRequest,
    ): Pair<PlaceReinforcementsErrorResponse, GameState> {
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
        lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = state)
        routingService.start(serverScope)

        val client =
            createClient {
                install(WebSockets)
            }

        return try {
            coroutineScope {
                val requesterSession =
                    connectSessionWithConnection(
                        client = client,
                        network = network,
                        playerId = requesterPlayerId,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                    )

                requesterSession.first.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(request),
                    ),
                )

                val error =
                    assertIs<PlaceReinforcementsErrorResponse>(
                        receiveRelevantTestPayload(requesterSession.first),
                    )
                assertNull(receiveRelevantTestPayloadOrNull(requesterSession.first))

                val snapshot =
                    lobbyManager.getLobby(lobbyCode)?.currentState()
                        ?: error("snapshot missing")
                requesterSession.first.close()
                error to snapshot
            }
        } finally {
            routingService.stop()
            lobbyManager.shutdownAll()
            serverScope.cancel()
        }
    }

    private fun reinforcementGame(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        turnPhase: TurnPhase,
        pendingPlayerId: PlayerId,
        pendingAmount: Int,
        owners: Map<TerritoryId, PlayerId>,
        troopCounts: Map<TerritoryId, Int>,
    ): GameState {
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = defaultMapDefinition(),
                players = players,
                playerDisplayNames = players.associateWith { "Player ${it.value}" },
            )

        return baseState.copy(
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
            territoryStates =
                baseState.allTerritoryStates().associate { territoryState ->
                    val territoryId = territoryState.territoryId
                    territoryId to
                        TerritoryState(
                            territoryId = territoryId,
                            ownerId = owners[territoryId],
                            troopCount = troopCounts[territoryId] ?: 0,
                        )
                },
            pendingReinforcements = PendingReinforcements(pendingPlayerId, pendingAmount),
        )
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

    private suspend fun receiveAnyPayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any = receiveRelevantTestPayload(session)

    private inline fun <reified T> assertIs(value: Any?): T {
        require(value is T) {
            "Expected ${T::class.simpleName}, but was ${value?.let { it::class.simpleName }}."
        }
        return value
    }

    private fun defaultMapDefinition() =
        at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault()
}
