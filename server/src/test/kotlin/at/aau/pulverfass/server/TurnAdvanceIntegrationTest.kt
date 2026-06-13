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
import at.aau.pulverfass.shared.lobby.event.MatchEndReason
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.DeckState
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.HandState
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import at.aau.pulverfass.shared.message.lobby.event.ConnectionStatusUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostReason
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class TurnAdvanceIntegrationTest {
    @Test
    fun `active player can advance and lobby receives exactly one turn state update`() =
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

            val lobbyCode = LobbyCode("TA01")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val playerThree = PlayerId(3)
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
            lobbyManager.createLobby(
                lobbyCode = LobbyCode("TA99"),
                initialState =
                    runningTurnStateGame(
                        lobbyCode = LobbyCode("TA99"),
                        players = listOf(playerThree),
                        activePlayerId = playerThree,
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
                    val outsiderSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerThree,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    playerOneSession.first.send(
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

                    assertEquals(
                        TurnAdvanceResponse(lobbyCode),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.REINFORCEMENTS,
                            nextPhase = TurnPhase.ATTACK,
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.REINFORCEMENTS,
                            nextPhase = TurnPhase.ATTACK,
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receivePayload(playerTwoSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                        receivePayload(playerTwoSession.first),
                    )
                    assertNull(receivePayloadOrNull(outsiderSession.first))
                    assertNull(receivePayloadOrNull(playerOneSession.first))
                    assertEquals(
                        TurnPhase.ATTACK,
                        lobbyManager.getLobby(lobbyCode)?.currentState()?.activeTurnPhase,
                    )

                    playerOneSession.first.close()
                    playerTwoSession.first.close()
                    outsiderSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `phase advance sends delta then boundary then turn update in deterministic order`() =
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

            val lobbyCode = LobbyCode("TA08")
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
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        expectedPhase = TurnPhase.REINFORCEMENTS,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    TurnStateUpdatedEvent(
                                        lobbyCode = lobbyCode,
                                        activePlayerId = playerOne,
                                        turnPhase = TurnPhase.ATTACK,
                                        turnCount = 1,
                                        startPlayerId = playerOne,
                                    ),
                                ),
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        TurnAdvanceResponse(lobbyCode),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.REINFORCEMENTS,
                            nextPhase = TurnPhase.ATTACK,
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )

                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    TurnStateUpdatedEvent(
                                        lobbyCode = lobbyCode,
                                        activePlayerId = playerOne,
                                        turnPhase = TurnPhase.ATTACK,
                                        turnCount = 1,
                                        startPlayerId = playerOne,
                                    ),
                                ),
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 1,
                            previousPhase = TurnPhase.REINFORCEMENTS,
                            nextPhase = TurnPhase.ATTACK,
                            activePlayerId = playerOne,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                        receiveAnyPayload(playerTwoSession.first),
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

    @Test
    fun `turn change broadcasts full public snapshot after turn state update`() =
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

            val lobbyCode = LobbyCode("TA09")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
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
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        expectedPhase = TurnPhase.DRAW_CARD,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events =
                                listOf(
                                    TurnStateUpdatedEvent(
                                        lobbyCode = lobbyCode,
                                        activePlayerId = playerTwo,
                                        turnPhase = TurnPhase.REINFORCEMENTS,
                                        turnCount = 1,
                                        startPlayerId = playerOne,
                                    ),
                                ),
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 2,
                            toVersion = 2,
                            events =
                                listOf(
                                    ReinforcementsGrantedEvent(
                                        lobbyCode = lobbyCode,
                                        playerId = playerTwo,
                                        amount = 3,
                                        territoryBonus = 3,
                                        continentBonus = 0,
                                        cardBonus = 0,
                                    ),
                                ),
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 2,
                            previousPhase = TurnPhase.DRAW_CARD,
                            nextPhase = TurnPhase.REINFORCEMENTS,
                            activePlayerId = playerTwo,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )

                    val snapshot =
                        assertIs<GameStateSnapshotBroadcast>(
                            receiveAnyPayload(playerTwoSession.first),
                        )
                    assertEquals(lobbyCode, snapshot.lobbyCode)
                    assertEquals(2, snapshot.stateVersion)
                    assertEquals(defaultMapDefinition().mapHash, snapshot.determinism.mapHash)
                    assertEquals(
                        defaultMapDefinition().schemaVersion,
                        snapshot.determinism.schemaVersion,
                    )
                    assertEquals(playerTwo, snapshot.turnState.activePlayerId)
                    assertEquals(TurnPhase.REINFORCEMENTS, snapshot.turnState.turnPhase)
                    assertEquals(1, snapshot.turnState.turnCount)
                    assertEquals(playerOne, snapshot.turnState.startPlayerId)
                    assertEquals(24, snapshot.territoryStates.size)
                    assertEquals(24, snapshot.definition.territories.size)

                    playerOneSession.first.close()
                    playerTwoSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `draw card phase sends exactly one private card after capture`() =
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

            val lobbyCode = LobbyCode("TA11")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val drawnCard = CardState(CardId("deck-1"), CardType.A)
            val remainingCard = CardState(CardId("deck-2"), CardType.B)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
                    ).copy(
                        deckState = DeckState(listOf(drawnCard, remainingCard)),
                        territoryCapturedThisTurn = true,
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
                    val expectedUpdate =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = playerOne,
                        )
                    val expectedReinforcements =
                        ReinforcementsGrantedEvent(
                            lobbyCode = lobbyCode,
                            playerId = playerTwo,
                            amount = 3,
                            territoryBonus = 3,
                            continentBonus = 0,
                            cardBonus = 0,
                        )

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        expectedPhase = TurnPhase.DRAW_CARD,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        GameStateDeltaEvent(lobbyCode, 2, 2, listOf(expectedUpdate)),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        PlayerHandUpdatedEvent(
                            lobbyCode = lobbyCode,
                            recipientPlayerId = playerOne,
                            stateVersion = 2,
                            handCards =
                                listOf(
                                    PrivateHandCardSnapshot(
                                        cardId = drawnCard.cardId,
                                        type = drawnCard.type,
                                    ),
                                ),
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        GameStateDeltaEvent(lobbyCode, 3, 3, listOf(expectedReinforcements)),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        TurnAdvanceResponse(lobbyCode),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 3,
                            previousPhase = TurnPhase.DRAW_CARD,
                            nextPhase = TurnPhase.REINFORCEMENTS,
                            activePlayerId = playerTwo,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(expectedUpdate, receiveAnyPayload(playerOneSession.first))
                    val snapshot =
                        assertIs<GameStateSnapshotBroadcast>(
                            receiveAnyPayload(playerOneSession.first),
                        )
                    assertEquals(3, snapshot.stateVersion)
                    assertEquals(playerTwo, snapshot.turnState.activePlayerId)

                    assertEquals(
                        GameStateDeltaEvent(lobbyCode, 2, 2, listOf(expectedUpdate)),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        GameStateDeltaEvent(lobbyCode, 3, 3, listOf(expectedReinforcements)),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 3,
                            previousPhase = TurnPhase.DRAW_CARD,
                            nextPhase = TurnPhase.REINFORCEMENTS,
                            activePlayerId = playerTwo,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerTwoSession.first),
                    )
                    assertEquals(expectedUpdate, receiveAnyPayload(playerTwoSession.first))
                    assertIs<GameStateSnapshotBroadcast>(receiveAnyPayload(playerTwoSession.first))
                    assertNull(receivePayloadOrNull(playerTwoSession.first))

                    val currentState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("current state missing")
                    assertEquals(listOf(drawnCard), currentState.handOf(playerOne))
                    assertEquals(listOf(remainingCard), currentState.deckState.cards)
                    assertEquals(false, currentState.territoryCapturedThisTurn)

                    playerOneSession.first.close()
                    playerTwoSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `draw card keeps success response when private hand update exceeds payload limit`() =
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
                    privateStatePayloadMaxBytes = 128,
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("TA13")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val drawnCard = CardState(CardId("drawn-card"), CardType.A)
            val existingHand =
                (1..16).map { index ->
                    CardState(CardId("existing-card-$index"), CardType.B)
                }
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
                    ).copy(
                        deckState = DeckState(listOf(drawnCard)),
                        handState =
                            HandState(
                                cardsByPlayer = mapOf(playerOne to existingHand),
                            ),
                        territoryCapturedThisTurn = true,
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
                    val expectedUpdate =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = playerOne,
                            isPaused = true,
                            pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                            pausedPlayerId = playerTwo,
                        )
                    val expectedReinforcements =
                        ReinforcementsGrantedEvent(
                            lobbyCode = lobbyCode,
                            playerId = playerTwo,
                            amount = 3,
                            territoryBonus = 3,
                            continentBonus = 0,
                            cardBonus = 0,
                        )

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        expectedPhase = TurnPhase.DRAW_CARD,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        GameStateDeltaEvent(lobbyCode, 2, 2, listOf(expectedUpdate)),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        GameStateDeltaEvent(lobbyCode, 3, 3, listOf(expectedReinforcements)),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        TurnAdvanceResponse(lobbyCode),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 3,
                            previousPhase = TurnPhase.DRAW_CARD,
                            nextPhase = TurnPhase.REINFORCEMENTS,
                            activePlayerId = playerTwo,
                            turnCount = 1,
                        ),
                        receiveAnyPayload(playerOneSession.first),
                    )
                    assertEquals(expectedUpdate, receiveAnyPayload(playerOneSession.first))
                    assertIs<GameStateSnapshotBroadcast>(receiveAnyPayload(playerOneSession.first))
                    assertNull(receivePayloadOrNull(playerOneSession.first))

                    val currentState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("current state missing")
                    assertEquals(existingHand + drawnCard, currentState.handOf(playerOne))
                    assertEquals(emptyList<CardState>(), currentState.deckState.cards)

                    playerOneSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `draw card phase ends game when deck is empty and draw is required`() =
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

            val lobbyCode = LobbyCode("TA12")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
                    ).copy(
                        deckState = DeckState(),
                        territoryCapturedThisTurn = true,
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

                    val request = TurnAdvanceRequest(lobbyCode, playerOne, TurnPhase.DRAW_CARD)
                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(request),
                        ),
                    )

                    assertEquals(
                        TurnAdvanceResponse(lobbyCode),
                        receivePayload(playerOneSession.first),
                    )
                    assertNull(receivePayloadOrNull(playerOneSession.first))

                    val finishedState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("current state missing")
                    assertEquals(GameStatus.FINISHED, finishedState.status)
                    assertEquals(MatchEndReason.DECK_EMPTY.name, finishedState.closedReason)
                    assertEquals(TurnPhase.DRAW_CARD, finishedState.activeTurnPhase)
                    assertEquals(playerOne, finishedState.activePlayer)
                    assertEquals(emptyList<CardState>(), finishedState.handOf(playerOne))
                    assertEquals(emptyList<CardState>(), finishedState.deckState.cards)
                    assertEquals(false, finishedState.territoryCapturedThisTurn)

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(request),
                        ),
                    )
                    val error =
                        assertIs<TurnAdvanceErrorResponse>(
                            receivePayload(playerOneSession.first),
                        )
                    assertEquals(TurnAdvanceErrorCode.GAME_FINISHED, error.code)
                    assertNull(receivePayloadOrNull(playerOneSession.first))

                    playerOneSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `non active player gets not active player error and no state change`() =
        testApplication {
            val result =
                exerciseFailingAdvance(
                    lobbyCode = LobbyCode("TA02"),
                    state =
                        runningTurnStateGame(
                            lobbyCode = LobbyCode("TA02"),
                            players = listOf(PlayerId(1), PlayerId(2)),
                            activePlayerId = PlayerId(1),
                            turnPhase = TurnPhase.ATTACK,
                        ),
                    requesterPlayerId = PlayerId(2),
                    request =
                        TurnAdvanceRequest(
                            lobbyCode = LobbyCode("TA02"),
                            playerId = PlayerId(2),
                            expectedPhase = TurnPhase.ATTACK,
                        ),
                )

            assertEquals(TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER, result.first.code)
            assertEquals(TurnPhase.ATTACK, result.second.activeTurnPhase)
        }

    @Test
    fun `paused game gets game paused error and no state change`() =
        testApplication {
            val result =
                exerciseFailingAdvance(
                    lobbyCode = LobbyCode("TA03"),
                    state =
                        runningTurnStateGame(
                            lobbyCode = LobbyCode("TA03"),
                            players = listOf(PlayerId(1), PlayerId(2)),
                            activePlayerId = PlayerId(1),
                            turnPhase = TurnPhase.FORTIFY,
                            isPaused = true,
                            pauseReason = "manual-pause",
                        ),
                    requesterPlayerId = PlayerId(1),
                    request =
                        TurnAdvanceRequest(
                            lobbyCode = LobbyCode("TA03"),
                            playerId = PlayerId(1),
                            expectedPhase = TurnPhase.FORTIFY,
                        ),
                )

            assertEquals(TurnAdvanceErrorCode.GAME_PAUSED, result.first.code)
            assertEquals(TurnPhase.FORTIFY, result.second.activeTurnPhase)
            assertEquals(true, result.second.turnState?.isPaused)
        }

    @Test
    fun `phase mismatch gets phase mismatch error and no state change`() =
        testApplication {
            val result =
                exerciseFailingAdvance(
                    lobbyCode = LobbyCode("TA04"),
                    state =
                        runningTurnStateGame(
                            lobbyCode = LobbyCode("TA04"),
                            players = listOf(PlayerId(1), PlayerId(2)),
                            activePlayerId = PlayerId(1),
                            turnPhase = TurnPhase.DRAW_CARD,
                        ),
                    requesterPlayerId = PlayerId(1),
                    request =
                        TurnAdvanceRequest(
                            lobbyCode = LobbyCode("TA04"),
                            playerId = PlayerId(1),
                            expectedPhase = TurnPhase.ATTACK,
                        ),
                )

            assertEquals(TurnAdvanceErrorCode.PHASE_MISMATCH, result.first.code)
            assertEquals(TurnPhase.DRAW_CARD, result.second.activeTurnPhase)
        }

    @Test
    fun `turn advance from reinforcements requires forced card trade in`() =
        testApplication {
            val lobbyCode = LobbyCode("TA14")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val handCards =
                listOf(
                    CardState(CardId("forced-a-1"), CardType.A),
                    CardState(CardId("forced-a-2"), CardType.A),
                    CardState(CardId("forced-a-3"), CardType.A),
                    CardState(CardId("forced-b-1"), CardType.B),
                    CardState(CardId("forced-c-1"), CardType.C),
                )
            val result =
                exerciseFailingAdvance(
                    lobbyCode = lobbyCode,
                    state =
                        runningTurnStateGame(
                            lobbyCode = lobbyCode,
                            players = listOf(playerOne, playerTwo),
                            activePlayerId = playerOne,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                        ).copy(
                            handState =
                                HandState(
                                    cardsByPlayer = mapOf(playerOne to handCards),
                                ),
                        ),
                    requesterPlayerId = playerOne,
                    request =
                        TurnAdvanceRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                            expectedPhase = TurnPhase.REINFORCEMENTS,
                        ),
                )

            assertEquals(TurnAdvanceErrorCode.FORCED_TRADE_REQUIRED, result.first.code)
            assertEquals(TurnPhase.REINFORCEMENTS, result.second.activeTurnPhase)
            assertEquals(handCards, result.second.handOf(playerOne))
        }

    @Test
    fun `eliminated spectator gets not active player error and no state change`() =
        testApplication {
            val lobbyCode = LobbyCode("TA10")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                runningTurnStateGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.ATTACK,
                )
            val spectatorState =
                baseState.copy(
                    turnOrder = listOf(playerOne),
                    territoryStates =
                        baseState.allTerritoryStates().associate { territoryState ->
                            territoryState.territoryId to
                                TerritoryState(
                                    territoryId = territoryState.territoryId,
                                    ownerId = playerOne,
                                    troopCount = 1,
                                )
                        },
                )

            val result =
                exerciseFailingAdvance(
                    lobbyCode = lobbyCode,
                    state = spectatorState,
                    requesterPlayerId = playerTwo,
                    request =
                        TurnAdvanceRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerTwo,
                            expectedPhase = TurnPhase.ATTACK,
                        ),
                )

            assertEquals(TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER, result.first.code)
            assertEquals(listOf(playerOne), result.second.turnOrder)
            assertTrue(result.second.isSpectator(playerTwo))
        }

    @Test
    fun `disconnecting a non active player does not pause the game`() =
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

            val lobbyCode = LobbyCode("TA05")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
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

                    disconnectPlayer(
                        playerId = playerTwo,
                        session = playerTwoSession.first,
                        connectionId = playerTwoSession.second,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                        routingService = routingService,
                    )

                    assertEquals(
                        PlayerConnectionLostEvent(
                            lobbyCode = lobbyCode,
                            playerId = playerTwo,
                            reason = PlayerConnectionLostReason.SOCKET_CLOSED,
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    val snapshot =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("snapshot missing")
                    assertEquals(playerOne, snapshot.activePlayer)
                    assertEquals(false, snapshot.turnState?.isPaused)

                    playerOneSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `advance pauses when next active player is disconnected`() =
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

            val lobbyCode = LobbyCode("TA06")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.DRAW_CARD,
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

                    disconnectPlayer(
                        playerId = playerTwo,
                        session = playerTwoSession.first,
                        connectionId = playerTwoSession.second,
                        playersByConnection = playersByConnection,
                        connectionsByPlayer = connectionsByPlayer,
                        routingService = routingService,
                    )
                    assertEquals(
                        PlayerConnectionLostEvent(
                            lobbyCode = lobbyCode,
                            playerId = playerTwo,
                            reason = PlayerConnectionLostReason.SOCKET_CLOSED,
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        ConnectionStatusUpdateEvent(
                            lobbyCode = lobbyCode,
                            playerId = playerTwo,
                            status = ConnectionStatus.DISCONNECTED,
                        ),
                        receivePayload(playerOneSession.first),
                    )

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        expectedPhase = TurnPhase.DRAW_CARD,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        TurnAdvanceResponse(lobbyCode),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        PhaseBoundaryEvent(
                            lobbyCode = lobbyCode,
                            stateVersion = 2,
                            previousPhase = TurnPhase.DRAW_CARD,
                            nextPhase = TurnPhase.REINFORCEMENTS,
                            activePlayerId = playerTwo,
                            turnCount = 1,
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    assertEquals(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = playerOne,
                            isPaused = true,
                            pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                            pausedPlayerId = playerTwo,
                        ),
                        receivePayload(playerOneSession.first),
                    )
                    val snapshot =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("snapshot missing")
                    assertEquals(playerTwo, snapshot.activePlayer)
                    assertEquals(true, snapshot.turnState?.isPaused)
                    assertEquals(
                        TurnPauseReasons.WAITING_FOR_PLAYER,
                        snapshot.turnState?.pauseReason,
                    )
                    assertEquals(playerTwo, snapshot.turnState?.pausedPlayerId)
                    assertEquals(3, snapshot.pendingReinforcementsFor(playerTwo))

                    playerOneSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `reconnect resumes paused turn and keeps active player unchanged`() =
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

            val lobbyCode = LobbyCode("TA07")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    runningTurnStateGame(
                        lobbyCode = lobbyCode,
                        players = listOf(playerOne, playerTwo),
                        activePlayerId = playerTwo,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        isPaused = true,
                        pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                        pausedPlayerId = playerTwo,
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

                    routingService.onPlayerConnected(playerTwo)

                    val expectedEvent =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = playerTwo,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = playerOne,
                            isPaused = false,
                            pauseReason = null,
                            pausedPlayerId = null,
                        )
                    assertEquals(expectedEvent, receivePayload(playerOneSession.first))
                    assertEquals(expectedEvent, receivePayload(playerTwoSession.first))

                    val snapshot =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("snapshot missing")
                    assertEquals(playerTwo, snapshot.activePlayer)
                    assertEquals(false, snapshot.turnState?.isPaused)
                    assertEquals(null, snapshot.turnState?.pausedPlayerId)

                    playerOneSession.first.close()
                    playerTwoSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private suspend fun ApplicationTestBuilder.exerciseFailingAdvance(
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: TurnAdvanceRequest,
    ): Pair<TurnAdvanceErrorResponse, GameState> {
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
                    assertIs<TurnAdvanceErrorResponse>(receivePayload(requesterSession.first))
                assertNull(receivePayloadOrNull(requesterSession.first))

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

    private fun runningTurnStateGame(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
        activePlayerId: PlayerId,
        turnPhase: TurnPhase,
        isPaused: Boolean = false,
        pauseReason: String? = null,
        pausedPlayerId: PlayerId? = null,
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
                        isPaused = isPaused,
                        pauseReason = pauseReason,
                        pausedPlayerId = pausedPlayerId,
                    ),
                status = GameStatus.RUNNING,
            )
            .withAttackableTerritories(players)

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

    private suspend fun receiveAnyPayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any = receiveRelevantTestPayload(session)

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

    private fun defaultMapDefinition() =
        at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault()
}
