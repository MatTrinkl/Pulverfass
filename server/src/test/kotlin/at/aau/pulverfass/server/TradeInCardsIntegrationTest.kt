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
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.HandState
import at.aau.pulverfass.shared.lobby.state.PendingReinforcements
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.request.TradeInCardsRequest
import at.aau.pulverfass.shared.message.lobby.response.TradeInCardsResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorResponse
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

class TradeInCardsIntegrationTest {
    @Test
    fun `valid trade increases pending updates hand privately and grants public bonus`() =
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

            val lobbyCode = LobbyCode("TRI1")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val cardA = CardState(CardId("card-a"), CardType.A)
            val cardB = CardState(CardId("card-b"), CardType.B)
            val cardJ = CardState(CardId("card-j"), CardType.JOKER)
            val remainingCard = CardState(CardId("card-c"), CardType.C)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    reinforcementTradeGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        pendingPlayerId = playerOne,
                        pendingAmount = 3,
                        handState =
                            HandState(
                                cardsByPlayer =
                                    mapOf(
                                        playerOne to listOf(cardA, cardB, cardJ, remainingCard),
                                    ),
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
                                    TradeInCardsRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        cardIds = listOf(cardA.cardId, cardB.cardId, cardJ.cardId),
                                    ),
                                ),
                        ),
                    )

                    val grantDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    ReinforcementsGrantedEvent(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        amount = 2,
                                        territoryBonus = 0,
                                        continentBonus = 0,
                                        cardBonus = 2,
                                    ),
                                ),
                        )
                    val pendingDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 2,
                            toVersion = 2,
                            events =
                                listOf(
                                    PendingReinforcementsChangedEvent(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        delta = 2,
                                    ),
                                ),
                        )

                    assertEquals(grantDelta, receiveRelevantTestPayload(actorSession.first))
                    assertEquals(pendingDelta, receiveRelevantTestPayload(actorSession.first))
                    assertEquals(
                        TradeInCardsResponse(lobbyCode),
                        receiveRelevantTestPayload(actorSession.first),
                    )
                    assertEquals(
                        PlayerHandUpdatedEvent(
                            lobbyCode = lobbyCode,
                            recipientPlayerId = playerOne,
                            stateVersion = 3,
                            handCards =
                                listOf(
                                    PrivateHandCardSnapshot(
                                        remainingCard.cardId,
                                        remainingCard.type,
                                    ),
                                ),
                        ),
                        receiveRelevantTestPayload(actorSession.first),
                    )

                    assertEquals(grantDelta, receiveRelevantTestPayload(watcherSession.first))
                    assertEquals(pendingDelta, receiveRelevantTestPayload(watcherSession.first))

                    val updatedState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(5, updatedState.pendingReinforcementsFor(playerOne))
                    assertEquals(1, updatedState.tradedInSetCount)
                    assertEquals(listOf(remainingCard), updatedState.handOf(playerOne))
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
    fun `invalid trade is rejected when set is not valid`() =
        testApplication {
            val playerOne = PlayerId(1)
            val invalidA = CardState(CardId("card-a"), CardType.A)
            val invalidB = CardState(CardId("card-b"), CardType.A)
            val invalidC = CardState(CardId("card-c"), CardType.B)
            val state =
                reinforcementTradeGame(
                    lobbyCode = LobbyCode("TRI2"),
                    players = listOf(playerOne, PlayerId(2)),
                    activePlayerId = playerOne,
                    pendingPlayerId = playerOne,
                    pendingAmount = 1,
                    handState =
                        HandState(
                            cardsByPlayer =
                                mapOf(
                                    playerOne to listOf(invalidA, invalidB, invalidC),
                                ),
                        ),
                )

            val (error, snapshot) =
                exerciseFailingTrade(
                    lobbyCode = LobbyCode("TRI2"),
                    state = state,
                    requesterPlayerId = playerOne,
                    request =
                        TradeInCardsRequest(
                            lobbyCode = LobbyCode("TRI2"),
                            playerId = playerOne,
                            cardIds = listOf(invalidA.cardId, invalidB.cardId, invalidC.cardId),
                        ),
                )

            assertEquals(TradeInCardsErrorCode.INVALID_SET, error.code)
            assertEquals(1, snapshot.pendingReinforcementsFor(playerOne))
            assertEquals(0, snapshot.tradedInSetCount)
            assertEquals(listOf(invalidA, invalidB, invalidC), snapshot.handOf(playerOne))
        }

    @Test
    fun `security rejects trade when cards are not owned by requester`() =
        testApplication {
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val ownA = CardState(CardId("card-a"), CardType.A)
            val ownB = CardState(CardId("card-b"), CardType.B)
            val foreignJ = CardState(CardId("card-j"), CardType.JOKER)
            val state =
                reinforcementTradeGame(
                    lobbyCode = LobbyCode("TRI3"),
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    pendingPlayerId = playerOne,
                    pendingAmount = 4,
                    handState =
                        HandState(
                            cardsByPlayer =
                                mapOf(
                                    playerOne to listOf(ownA, ownB),
                                    playerTwo to listOf(foreignJ),
                                ),
                        ),
                )

            val (error, snapshot) =
                exerciseFailingTrade(
                    lobbyCode = LobbyCode("TRI3"),
                    state = state,
                    requesterPlayerId = playerOne,
                    request =
                        TradeInCardsRequest(
                            lobbyCode = LobbyCode("TRI3"),
                            playerId = playerOne,
                            cardIds = listOf(ownA.cardId, ownB.cardId, foreignJ.cardId),
                        ),
                )

            assertEquals(TradeInCardsErrorCode.CARDS_NOT_OWNED, error.code)
            assertEquals(4, snapshot.pendingReinforcementsFor(playerOne))
            assertEquals(0, snapshot.tradedInSetCount)
            assertEquals(listOf(ownA, ownB), snapshot.handOf(playerOne))
            assertEquals(listOf(foreignJ), snapshot.handOf(playerTwo))
        }

    private suspend fun ApplicationTestBuilder.exerciseFailingTrade(
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: TradeInCardsRequest,
    ): Pair<TradeInCardsErrorResponse, GameState> {
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

                val response =
                    receiveRelevantTestPayload(requesterSession.first) as TradeInCardsErrorResponse
                requesterSession.first.close()
                response to (lobbyManager.getLobby(lobbyCode)?.currentState() ?: error("missing"))
            }
        } finally {
            routingService.stop()
            lobbyManager.shutdownAll()
            serverScope.cancel()
        }
    }

    private fun reinforcementTradeGame(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        pendingPlayerId: PlayerId,
        pendingAmount: Int,
        handState: HandState,
    ): GameState {
        val mapDefinition = at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault()
        val turnState =
            TurnState(
                activePlayerId = activePlayerId,
                turnPhase = TurnPhase.REINFORCEMENTS,
                turnCount = 1,
                startPlayerId = players.first(),
            )

        return GameState.initial(
            lobbyCode = lobbyCode,
            mapDefinition = mapDefinition,
            players = players,
            playerDisplayNames = players.associateWith { playerId -> "Player ${playerId.value}" },
        ).copy(
            activePlayer = activePlayerId,
            turnState = turnState,
            turnNumber = turnState.turnCount,
            status = at.aau.pulverfass.shared.lobby.state.GameStatus.RUNNING,
            pendingReinforcements = PendingReinforcements(pendingPlayerId, pendingAmount),
            handState = handState,
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
}
