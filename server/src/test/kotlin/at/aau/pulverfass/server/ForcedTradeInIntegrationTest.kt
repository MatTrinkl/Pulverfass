package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.HandState
import at.aau.pulverfass.shared.lobby.state.PendingReinforcements
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import at.aau.pulverfass.shared.message.lobby.request.ConfirmReinforcementsDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequest
import at.aau.pulverfass.shared.message.lobby.request.TerritoryPlacement
import at.aau.pulverfass.shared.message.lobby.response.ConfirmReinforcementsDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.PlaceReinforcementsResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponse
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

class ForcedTradeInIntegrationTest {
    private val mapDefinition = MapConfigLoader.loadDefault()
    private val firstTerritoryId = mapDefinition.territories.first().territoryId
    private val playerOne = PlayerId(1)
    private val playerTwo = PlayerId(2)

    // 5-card hand that contains a valid set (A, B, C present → A+B+C is valid).
    private val fiveCardHandWithSet =
        listOf(
            CardState(CardId("a1"), CardType.A),
            CardState(CardId("a2"), CardType.A),
            CardState(CardId("b1"), CardType.B),
            CardState(CardId("b2"), CardType.B),
            CardState(CardId("c1"), CardType.C),
        )

    // 4-card hand that contains a valid set (below forced-trade threshold).
    private val fourCardHandWithSet =
        listOf(
            CardState(CardId("x1"), CardType.A),
            CardState(CardId("x2"), CardType.B),
            CardState(CardId("x3"), CardType.C),
            CardState(CardId("x4"), CardType.A),
        )

    @Test
    fun `confirm reinforcements done is blocked when 5 cards and can form set`() =
        testApplication {
            val lobbyCode = LobbyCode("FTI1")
            val state =
                forcedTradeState(
                    lobbyCode = lobbyCode,
                    hand = fiveCardHandWithSet,
                    pendingAmount = 0,
                )

            val error =
                exerciseFailingConfirm(
                    lobbyCode = lobbyCode,
                    state = state,
                    request =
                        ConfirmReinforcementsDoneRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                        ),
                )

            assertEquals(ConfirmReinforcementsDoneErrorCode.FORCED_TRADE_REQUIRED, error.code)
        }

    @Test
    fun `place reinforcements is blocked when 5 cards and can form set`() =
        testApplication {
            val lobbyCode = LobbyCode("FTI2")
            val state =
                forcedTradeState(
                    lobbyCode = lobbyCode,
                    hand = fiveCardHandWithSet,
                    pendingAmount = 3,
                    ownedTerritoryId = firstTerritoryId,
                )

            val error =
                exerciseFailingPlace(
                    lobbyCode = lobbyCode,
                    state = state,
                    request =
                        PlaceReinforcementsRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                            placements = listOf(TerritoryPlacement(firstTerritoryId, 1)),
                        ),
                )

            assertEquals(PlaceReinforcementsErrorCode.FORCED_TRADE_REQUIRED, error.code)
        }

    @Test
    fun `confirm reinforcements done is allowed when exactly 4 cards even with valid set`() =
        testApplication {
            val lobbyCode = LobbyCode("FTI3")
            val state =
                forcedTradeState(
                    lobbyCode = lobbyCode,
                    hand = fourCardHandWithSet,
                    pendingAmount = 0,
                )

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
                    playerIdResolver = { id -> playersByConnection[id] },
                    connectionIdResolver = { id -> connectionsByPlayer[id] },
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            application { module(network) }
            lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = state)
            routingService.start(serverScope)

            val client = createClient { install(WebSockets) }

            try {
                coroutineScope {
                    val session =
                        connectSession(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    session.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ConfirmReinforcementsDoneRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                    ),
                                ),
                        ),
                    )

                    val response = receiveRelevantTestPayload(session, skipGameSync = true)
                    session.close()

                    assertEquals(ConfirmReinforcementsDoneResponse(lobbyCode), response)
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `place reinforcements is allowed when exactly 4 cards even with valid set`() =
        testApplication {
            val lobbyCode = LobbyCode("FTI4")
            val state =
                forcedTradeState(
                    lobbyCode = lobbyCode,
                    hand = fourCardHandWithSet,
                    pendingAmount = 3,
                    ownedTerritoryId = firstTerritoryId,
                )

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
                    playerIdResolver = { id -> playersByConnection[id] },
                    connectionIdResolver = { id -> connectionsByPlayer[id] },
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            application { module(network) }
            lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = state)
            routingService.start(serverScope)

            val client = createClient { install(WebSockets) }

            try {
                coroutineScope {
                    val session =
                        connectSession(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    session.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    PlaceReinforcementsRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        placements =
                                            listOf(
                                                TerritoryPlacement(firstTerritoryId, 1),
                                            ),
                                    ),
                                ),
                        ),
                    )

                    receiveRelevantTestPayload(session) // GameStateDeltaEvent (troop change)
                    receiveRelevantTestPayload(session) // TerritoryTroopsChangedEvent direct
                    receiveRelevantTestPayload(session) // GameStateDeltaEvent (pending change)
                    val response = receiveRelevantTestPayload(session)
                    session.close()

                    assertEquals(PlaceReinforcementsResponse(lobbyCode = lobbyCode), response)
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `confirm reinforcements done is blocked by next phase trade flag from elimination`() =
        testApplication {
            val lobbyCode = LobbyCode("FTI5")
            val state =
                forcedTradeState(
                    lobbyCode = lobbyCode,
                    hand = fourCardHandWithSet,
                    pendingAmount = 0,
                ).copy(
                    tradeRequiredOnNextReinforcementPhaseByPlayer =
                        mapOf(
                            playerOne to true,
                            playerTwo to false,
                        ),
                )

            val error =
                exerciseFailingConfirm(
                    lobbyCode = lobbyCode,
                    state = state,
                    request =
                        ConfirmReinforcementsDoneRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                        ),
                )

            assertEquals(ConfirmReinforcementsDoneErrorCode.FORCED_TRADE_REQUIRED, error.code)
        }

    private suspend fun ApplicationTestBuilder.exerciseFailingConfirm(
        lobbyCode: LobbyCode,
        state: GameState,
        request: ConfirmReinforcementsDoneRequest,
    ): ConfirmReinforcementsDoneErrorResponse {
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
                playerIdResolver = { id -> playersByConnection[id] },
                connectionIdResolver = { id -> connectionsByPlayer[id] },
                hooks = MainServerLobbyRoutingServiceHooks(),
            )

        application { module(network) }
        lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = state)
        routingService.start(serverScope)

        val client = createClient { install(WebSockets) }

        return try {
            coroutineScope {
                val session =
                    connectSession(
                        client = client,
                        network = network,
                        playerId = playerOne,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                    )

                session.send(Frame.Binary(fin = true, data = MessageCodec.encode(request)))

                val response =
                    receiveRelevantTestPayload(session) as ConfirmReinforcementsDoneErrorResponse
                assertNull(receiveRelevantTestPayloadOrNull(session))
                session.close()
                response
            }
        } finally {
            routingService.stop()
            lobbyManager.shutdownAll()
            serverScope.cancel()
        }
    }

    private suspend fun ApplicationTestBuilder.exerciseFailingPlace(
        lobbyCode: LobbyCode,
        state: GameState,
        request: PlaceReinforcementsRequest,
    ): PlaceReinforcementsErrorResponse {
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
                playerIdResolver = { id -> playersByConnection[id] },
                connectionIdResolver = { id -> connectionsByPlayer[id] },
                hooks = MainServerLobbyRoutingServiceHooks(),
            )

        application { module(network) }
        lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = state)
        routingService.start(serverScope)

        val client = createClient { install(WebSockets) }

        return try {
            coroutineScope {
                val session =
                    connectSession(
                        client = client,
                        network = network,
                        playerId = playerOne,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                    )

                session.send(Frame.Binary(fin = true, data = MessageCodec.encode(request)))

                val response =
                    receiveRelevantTestPayload(session) as PlaceReinforcementsErrorResponse
                assertNull(receiveRelevantTestPayloadOrNull(session))
                session.close()
                response
            }
        } finally {
            routingService.stop()
            lobbyManager.shutdownAll()
            serverScope.cancel()
        }
    }

    private suspend fun connectSession(
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
        session
    }

    private fun forcedTradeState(
        lobbyCode: LobbyCode,
        hand: List<CardState>,
        pendingAmount: Int,
        ownedTerritoryId: TerritoryId? = null,
    ): GameState {
        val players = listOf(playerOne, playerTwo)
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = mapDefinition,
                players = players,
                playerDisplayNames = players.associateWith { "Player ${it.value}" },
            )

        val updatedTerritories =
            baseState.allTerritoryStates().associate { ts ->
                ts.territoryId to
                    TerritoryState(
                        territoryId = ts.territoryId,
                        ownerId = if (ts.territoryId == ownedTerritoryId) playerOne else null,
                        troopCount = if (ts.territoryId == ownedTerritoryId) 3 else 0,
                    )
            }

        return baseState.copy(
            lobbyOwner = playerOne,
            activePlayer = playerOne,
            turnOrder = players,
            turnNumber = 1,
            turnState =
                TurnState(
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    turnCount = 1,
                    startPlayerId = playerOne,
                ),
            status = GameStatus.RUNNING,
            pendingReinforcements = PendingReinforcements(playerOne, pendingAmount),
            handState = HandState(cardsByPlayer = mapOf(playerOne to hand)),
            territoryStates = updatedTerritories,
        )
    }
}
