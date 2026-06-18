package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.PendingReinforcements
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.request.ClaimCheatReinforcementBonusRequest
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequest
import at.aau.pulverfass.shared.message.lobby.request.ReportCheatRequest
import at.aau.pulverfass.shared.message.lobby.request.TerritoryPlacement
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.response.ClaimCheatReinforcementBonusResponse
import at.aau.pulverfass.shared.message.lobby.response.PlaceReinforcementsResponse
import at.aau.pulverfass.shared.message.lobby.response.ReportCheatResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ClaimCheatReinforcementBonusErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ClaimCheatReinforcementBonusErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ReportCheatErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ReportCheatErrorResponse
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ClaimCheatReinforcementBonusIntegrationTest {
    @Test
    fun `valid request grants three pending reinforcements and marks bonus as used`() =
        testApplication {
            val lobbyCode = LobbyCode("CHS1")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
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
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            application {
                module(network)
            }
            lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = baseState)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val session =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    session.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ClaimCheatReinforcementBonusRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                    ),
                                ),
                        ),
                    )

                    val delta =
                        assertIs<GameStateDeltaEvent>(receiveRelevantTestPayload(session.first))
                    assertEquals(
                        listOf(
                            PendingReinforcementsChangedEvent(
                                lobbyCode = lobbyCode,
                                playerId = playerOne,
                                delta = 3,
                            ),
                        ),
                        delta.events,
                    )

                    assertEquals(
                        ClaimCheatReinforcementBonusResponse(lobbyCode),
                        receiveRelevantTestPayload(session.first),
                    )

                    val updatedState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(5, updatedState.pendingReinforcementsFor(playerOne))
                    assertTrue(playerOne in updatedState.usedCheatReinforcementBonusByPlayer)

                    session.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `correct cheat report rewards reporter and zeroes cheater reinforcements`() =
        testApplication {
            val lobbyCode = LobbyCode("CHR1")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                )
            val placementTerritoryId = baseState.allTerritoryStates().first().territoryId

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
            lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = baseState)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val cheaterSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val reporterSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    cheaterSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ClaimCheatReinforcementBonusRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                    ),
                                ),
                        ),
                    )

                    assertIs<GameStateDeltaEvent>(receiveRelevantTestPayload(cheaterSession.first))
                    assertEquals(
                        ClaimCheatReinforcementBonusResponse(lobbyCode),
                        receiveRelevantTestPayload(cheaterSession.first),
                    )

                    // Erst die Platzierung macht den Cheat für die anderen Spieler sichtbar.
                    cheaterSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    PlaceReinforcementsRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        placements =
                                            listOf(
                                                TerritoryPlacement(
                                                    territoryId = placementTerritoryId,
                                                    amount = 1,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    )

                    // Beide Clients bekommen die sichtbare Truppenänderung.
                    assertIs<TerritoryTroopsChangedEvent>(
                        receiveRelevantTestPayload(
                            session = cheaterSession.first,
                            skipGameSync = true,
                        ),
                    )
                    assertEquals(
                        PlaceReinforcementsResponse(lobbyCode),
                        receiveRelevantTestPayload(
                            session = cheaterSession.first,
                            skipGameSync = true,
                        ),
                    )
                    assertIs<TerritoryTroopsChangedEvent>(
                        receiveRelevantTestPayload(
                            session = reporterSession.first,
                            skipGameSync = true,
                        ),
                    )

                    // Danach liegt das Meldefenster offen und die Meldung muss korrekt sein.
                    reporterSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ReportCheatRequest(
                                        lobbyCode = lobbyCode,
                                        reporterPlayerId = playerTwo,
                                        accusedPlayerId = playerOne,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        ReportCheatResponse(
                            lobbyCode = lobbyCode,
                            accusedPlayerId = playerOne,
                            correct = true,
                            modifierDelta = 3,
                        ),
                        receiveRelevantTestPayload(
                            session = reporterSession.first,
                            skipGameSync = true,
                        ),
                    )

                    advanceUntilCheaterReinforcementPenaltyApplies(
                        cheaterSession = cheaterSession.first,
                        reporterSession = reporterSession.first,
                        lobbyCode = lobbyCode,
                        cheaterId = playerOne,
                        reporterId = playerTwo,
                    )

                    val penalizedState =
                        lobbyManager.getLobby(lobbyCode)?.currentState()
                            ?: error("state missing")
                    assertEquals(0, penalizedState.pendingReinforcementsFor(playerOne))

                    cheaterSession.first.close()
                    reporterSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `cheat report window expires twenty seconds after visible placement`() =
        testApplication {
            val lobbyCode = LobbyCode("CHR3")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val playerThree = PlayerId(3)
            val playerFour = PlayerId(4)
            val nowMillis = AtomicLong(1_000L)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo, playerThree, playerFour),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                )
            val placementTerritoryId = baseState.allTerritoryStates().first().territoryId

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
                    nowEpochMillis = nowMillis::get,
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            application {
                module(network)
            }
            lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = baseState)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val cheaterSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val reporterSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val lateReporterSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerThree,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val expiredReporterSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerFour,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    cheaterSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ClaimCheatReinforcementBonusRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                    ),
                                ),
                        ),
                    )

                    assertIs<GameStateDeltaEvent>(receiveRelevantTestPayload(cheaterSession.first))
                    assertEquals(
                        ClaimCheatReinforcementBonusResponse(lobbyCode),
                        receiveRelevantTestPayload(cheaterSession.first),
                    )

                    nowMillis.addAndGet(30_000L)
                    reporterSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ReportCheatRequest(
                                        lobbyCode = lobbyCode,
                                        reporterPlayerId = playerTwo,
                                        accusedPlayerId = playerOne,
                                    ),
                                ),
                        ),
                    )

                    // Auch lange vor der sichtbaren Platzierung bleibt der Cheat meldbar.
                    assertEquals(
                        ReportCheatResponse(
                            lobbyCode = lobbyCode,
                            accusedPlayerId = playerOne,
                            correct = true,
                            modifierDelta = 3,
                        ),
                        receiveRelevantTestPayload(
                            session = reporterSession.first,
                            skipGameSync = true,
                        ),
                    )

                    val placementMillis = nowMillis.get()
                    cheaterSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    PlaceReinforcementsRequest(
                                        lobbyCode = lobbyCode,
                                        playerId = playerOne,
                                        placements =
                                            listOf(
                                                TerritoryPlacement(
                                                    territoryId = placementTerritoryId,
                                                    amount = 1,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    )

                    assertIs<TerritoryTroopsChangedEvent>(
                        receiveRelevantTestPayload(
                            session = cheaterSession.first,
                            skipGameSync = true,
                        ),
                    )
                    assertEquals(
                        PlaceReinforcementsResponse(lobbyCode),
                        receiveRelevantTestPayload(
                            session = cheaterSession.first,
                            skipGameSync = true,
                        ),
                    )
                    assertIs<TerritoryTroopsChangedEvent>(
                        receiveRelevantTestPayload(
                            session = lateReporterSession.first,
                            skipGameSync = true,
                        ),
                    )
                    assertIs<TerritoryTroopsChangedEvent>(
                        receiveRelevantTestPayload(
                            session = expiredReporterSession.first,
                            skipGameSync = true,
                        ),
                    )

                    nowMillis.set(placementMillis + 19_999L)
                    lateReporterSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ReportCheatRequest(
                                        lobbyCode = lobbyCode,
                                        reporterPlayerId = playerThree,
                                        accusedPlayerId = playerOne,
                                    ),
                                ),
                        ),
                    )
                    assertEquals(
                        ReportCheatResponse(
                            lobbyCode = lobbyCode,
                            accusedPlayerId = playerOne,
                            correct = true,
                            modifierDelta = 3,
                        ),
                        receiveRelevantTestPayload(
                            session = lateReporterSession.first,
                            skipGameSync = true,
                        ),
                    )

                    nowMillis.set(placementMillis + 20_001L)
                    expiredReporterSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ReportCheatRequest(
                                        lobbyCode = lobbyCode,
                                        reporterPlayerId = playerFour,
                                        accusedPlayerId = playerOne,
                                    ),
                                ),
                        ),
                    )
                    assertEquals(
                        ReportCheatResponse(
                            lobbyCode = lobbyCode,
                            accusedPlayerId = playerOne,
                            correct = false,
                            modifierDelta = -3,
                        ),
                        receiveRelevantTestPayload(
                            session = expiredReporterSession.first,
                            skipGameSync = true,
                        ),
                    )

                    cheaterSession.first.close()
                    reporterSession.first.close()
                    lateReporterSession.first.close()
                    expiredReporterSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `reporting player without open cheat window penalizes reporter`() =
        testApplication {
            val lobbyCode = LobbyCode("CHR2")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
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
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            application {
                module(network)
            }
            lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = baseState)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val reporterSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = playerTwo,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    reporterSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    ReportCheatRequest(
                                        lobbyCode = lobbyCode,
                                        reporterPlayerId = playerTwo,
                                        accusedPlayerId = playerOne,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        ReportCheatResponse(
                            lobbyCode = lobbyCode,
                            accusedPlayerId = playerOne,
                            correct = false,
                            modifierDelta = -3,
                        ),
                        receiveRelevantTestPayload(reporterSession.first),
                    )

                    reporterSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `request is rejected when bonus was already used`() =
        testApplication {
            val lobbyCode = LobbyCode("CHS2")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                    usedCheatReinforcementBonusByPlayer = setOf(playerOne),
                )

            val (error, snapshot) =
                exerciseFailingClaim(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        ClaimCheatReinforcementBonusRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                        ),
                )

            assertEquals(ClaimCheatReinforcementBonusErrorCode.ALREADY_USED, error.code)
            assertEquals(2, snapshot.pendingReinforcementsFor(playerOne))
            assertTrue(playerOne in snapshot.usedCheatReinforcementBonusByPlayer)
        }

    @Test
    fun `request is rejected outside reinforcements phase`() =
        testApplication {
            val lobbyCode = LobbyCode("CHS3")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.ATTACK,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                )

            val (error, snapshot) =
                exerciseFailingClaim(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        ClaimCheatReinforcementBonusRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                        ),
                )

            assertEquals(ClaimCheatReinforcementBonusErrorCode.PHASE_MISMATCH, error.code)
            assertEquals(2, snapshot.pendingReinforcementsFor(playerOne))
        }

    @Test
    fun `request is rejected when requester does not match payload player`() =
        testApplication {
            val lobbyCode = LobbyCode("CHS4")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                )

            val (error, snapshot) =
                exerciseFailingClaim(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        ClaimCheatReinforcementBonusRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerTwo,
                        ),
                )

            assertEquals(ClaimCheatReinforcementBonusErrorCode.REQUESTER_MISMATCH, error.code)
            assertEquals(2, snapshot.pendingReinforcementsFor(playerOne))
            assertTrue(playerOne !in snapshot.usedCheatReinforcementBonusByPlayer)
        }

    @Test
    fun `request is rejected while game is paused`() =
        testApplication {
            val lobbyCode = LobbyCode("CHS5")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                ).let { state ->
                    state.copy(
                        turnState =
                            state.turnState?.copy(
                                isPaused = true,
                                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                                pausedPlayerId = playerOne,
                            ),
                    )
                }

            val (error, snapshot) =
                exerciseFailingClaim(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        ClaimCheatReinforcementBonusRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerOne,
                        ),
                )

            assertEquals(ClaimCheatReinforcementBonusErrorCode.GAME_PAUSED, error.code)
            assertEquals(2, snapshot.pendingReinforcementsFor(playerOne))
            assertTrue(playerOne !in snapshot.usedCheatReinforcementBonusByPlayer)
        }

    @Test
    fun `cheat report is rejected when requester does not match reporter`() =
        testApplication {
            val lobbyCode = LobbyCode("CRE1")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                )

            val (error, snapshot) =
                exerciseFailingReport(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        ReportCheatRequest(
                            lobbyCode = lobbyCode,
                            reporterPlayerId = playerTwo,
                            accusedPlayerId = playerOne,
                        ),
                )

            assertEquals(ReportCheatErrorCode.REQUESTER_MISMATCH, error.code)
            assertEquals(2, snapshot.pendingReinforcementsFor(playerOne))
        }

    @Test
    fun `cheat report is rejected when player reports himself`() =
        testApplication {
            val lobbyCode = LobbyCode("CRE2")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                )

            val (error, snapshot) =
                exerciseFailingReport(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        ReportCheatRequest(
                            lobbyCode = lobbyCode,
                            reporterPlayerId = playerOne,
                            accusedPlayerId = playerOne,
                        ),
                )

            assertEquals(ReportCheatErrorCode.SELF_REPORT, error.code)
            assertEquals(2, snapshot.pendingReinforcementsFor(playerOne))
        }

    @Test
    fun `cheat report is rejected for unknown accused player`() =
        testApplication {
            val lobbyCode = LobbyCode("CRE3")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val unknownPlayer = PlayerId(99)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                )

            val (error, snapshot) =
                exerciseFailingReport(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        ReportCheatRequest(
                            lobbyCode = lobbyCode,
                            reporterPlayerId = playerOne,
                            accusedPlayerId = unknownPlayer,
                        ),
                )

            assertEquals(ReportCheatErrorCode.UNKNOWN_PLAYER, error.code)
            assertEquals(2, snapshot.pendingReinforcementsFor(playerOne))
        }

    @Test
    fun `cheat report is rejected before game has started`() =
        testApplication {
            val lobbyCode = LobbyCode("CRE4")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val baseState =
                reinforcementGame(
                    lobbyCode = lobbyCode,
                    players = listOf(playerOne, playerTwo),
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    pendingPlayerId = playerOne,
                    pendingAmount = 2,
                ).copy(
                    gameStarted = false,
                    status = GameStatus.WAITING_FOR_PLAYERS,
                )

            val (error, snapshot) =
                exerciseFailingReport(
                    lobbyCode = lobbyCode,
                    state = baseState,
                    requesterPlayerId = playerOne,
                    request =
                        ReportCheatRequest(
                            lobbyCode = lobbyCode,
                            reporterPlayerId = playerOne,
                            accusedPlayerId = playerTwo,
                        ),
                )

            assertEquals(ReportCheatErrorCode.GAME_NOT_RUNNING, error.code)
            assertEquals(2, snapshot.pendingReinforcementsFor(playerOne))
        }

    private suspend fun ApplicationTestBuilder.exerciseFailingClaim(
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: ClaimCheatReinforcementBonusRequest,
    ): Pair<ClaimCheatReinforcementBonusErrorResponse, GameState> {
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
                    assertIs<ClaimCheatReinforcementBonusErrorResponse>(
                        receiveRelevantTestPayload(requesterSession.first),
                    )

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

    private suspend fun ApplicationTestBuilder.exerciseFailingReport(
        lobbyCode: LobbyCode,
        state: GameState,
        requesterPlayerId: PlayerId,
        request: ReportCheatRequest,
    ): Pair<ReportCheatErrorResponse, GameState> {
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
                    assertIs<ReportCheatErrorResponse>(
                        receiveRelevantTestPayload(requesterSession.first),
                    )

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
        usedCheatReinforcementBonusByPlayer: Set<PlayerId> = emptySet(),
    ): GameState {
        val mapDefinition = at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault()
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = mapDefinition,
                players = players,
                playerDisplayNames = players.associateWith { "Player ${it.value}" },
            )

        return baseState.copy(
            lobbyOwner = players.firstOrNull(),
            activePlayer = activePlayerId,
            turnOrder = players,
            turnNumber = 1,
            gameStarted = true,
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
                    territoryState.territoryId to
                        TerritoryState(
                            territoryId = territoryState.territoryId,
                            ownerId = players.first(),
                            troopCount = 1,
                        )
                },
            pendingReinforcements = PendingReinforcements(pendingPlayerId, pendingAmount),
            usedCheatReinforcementBonusByPlayer = usedCheatReinforcementBonusByPlayer,
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

    private suspend fun advanceTurn(
        session: DefaultClientWebSocketSession,
        lobbyCode: LobbyCode,
        playerId: PlayerId,
        expectedPhase: TurnPhase,
    ) {
        session.send(
            Frame.Binary(
                fin = true,
                data =
                    MessageCodec.encode(
                        TurnAdvanceRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerId,
                            expectedPhase = expectedPhase,
                        ),
                    ),
            ),
        )
        assertEquals(TurnAdvanceResponse(lobbyCode), receivePayloadOf<TurnAdvanceResponse>(session))
    }

    /**
     * Führt die Runde bis zur nächsten Verstärkungsphase des Cheaters fort.
     *
     * Fortify beendet den Zug direkt und setzt den nächsten Spieler in die
     * Verstärkungsphase. Die 0-Truppen-Strafe ist daher erst sichtbar, nachdem
     * der meldende Spieler seinen vollständigen Zug beendet hat.
     *
     * @param cheaterSession WebSocket-Verbindung des Spielers, dessen Cheat bestätigt wurde.
     * @param reporterSession WebSocket-Verbindung des Spielers, der den Cheat korrekt gemeldet hat.
     * @param lobbyCode Code der laufenden Lobby, in der die Phasen weitergeschaltet werden.
     * @param cheaterId Spieler, dessen nächste Verstärkungsphase auf 0 Truppen gesetzt werden soll.
     * @param reporterId Spieler, der vor der Strafprüfung seinen vollständigen Zug beendet.
     */
    private suspend fun advanceUntilCheaterReinforcementPenaltyApplies(
        cheaterSession: DefaultClientWebSocketSession,
        reporterSession: DefaultClientWebSocketSession,
        lobbyCode: LobbyCode,
        cheaterId: PlayerId,
        reporterId: PlayerId,
    ) {
        advanceTurn(cheaterSession, lobbyCode, cheaterId, TurnPhase.REINFORCEMENTS)
        advanceTurn(cheaterSession, lobbyCode, cheaterId, TurnPhase.ATTACK)
        advanceTurn(cheaterSession, lobbyCode, cheaterId, TurnPhase.FORTIFY)
        advanceTurn(reporterSession, lobbyCode, reporterId, TurnPhase.REINFORCEMENTS)
        advanceTurn(reporterSession, lobbyCode, reporterId, TurnPhase.ATTACK)
        advanceTurn(reporterSession, lobbyCode, reporterId, TurnPhase.FORTIFY)
    }

    private suspend inline fun <reified T> receivePayloadOf(
        session: DefaultClientWebSocketSession,
    ): T {
        repeat(50) {
            val payload = receiveRelevantTestPayload(session, skipGameSync = true)
            if (payload is T) {
                return payload
            }
        }
        throw AssertionError("Expected ${T::class.simpleName}.")
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        require(value is T) {
            "Expected ${T::class.simpleName}, but was ${value?.let { it::class.simpleName }}."
        }
        return value
    }
}
