package at.aau.pulverfass.app.lobby

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.storage.PlayerNameStore
import at.aau.pulverfass.app.storage.ReconnectSessionStore
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectErrorCode
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostReason
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.request.AttackRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmAttackDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmReinforcementsDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.TradeInCardsRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.AttackResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmAttackDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmReinforcementsDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PlaceReinforcementsResponse
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.TradeInCardsResponse
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LobbyControllerTest {
    @Test
    fun `default state should match lobby defaults`() {
        val controller = createController()
        try {
            val state = controller.state.value

            assertEquals("ws://5.189.160.80:8080/ws", state.serverUrl)
            assertEquals("", state.playerName)
            assertEquals("", state.lobbyCode)
            assertFalse(state.isJoining)
            assertFalse(state.isConnecting)
            assertFalse(state.isReconnecting)
            assertFalse(state.isConnected)
            assertEquals("Nicht verbunden", state.statusText)
            assertNull(state.errorText)
            assertNull(state.sessionToken)
            assertNull(state.lastMessageType)
            assertTrue(state.playerNames.isEmpty())
        } finally {
            controller.close()
        }
    }

    @Test
    fun `updatePlayerColor stores the chosen color in state`() {
        val controller = createController()
        try {
            val color = Color(0xFF6FD4C5)
            controller.updatePlayerColor(color)
            assertEquals(color, controller.state.value.playerColor)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `controller should restore and persist configured player name`() {
        val playerNameStore = InMemoryPlayerNameStore("Anne Bonny")
        val controller = createController(playerNameStore = playerNameStore)
        try {
            assertEquals("Anne Bonny", controller.state.value.playerName)

            controller.updatePlayerName("Mary Read")

            assertEquals("Mary Read", controller.state.value.playerName)
            assertEquals("Mary Read", playerNameStore.readPlayerName())
        } finally {
            controller.close()
        }
    }

    @Test
    fun `connect should fail fast when player name is blank`() {
        val controller = createController()
        try {
            controller.connect()

            val state = controller.state.value
            assertFalse(state.isConnecting)
            assertFalse(state.isConnected)
            assertEquals("Bitte zuerst einen Spielernamen eingeben", state.errorText)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `create lobby flow should auto-connect and navigate after create and join responses`() {
        runBlocking {
            val lobbyCode = LobbyCode("AB12")
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174200"),
                        ),
                ) { payload, outgoing ->
                    when (payload) {
                        CreateLobbyRequest -> {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(CreateLobbyResponse(lobbyCode)),
                                ),
                            )
                        }
                        is JoinLobbyRequest -> {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(JoinLobbyResponse(payload.lobbyCode)),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        PlayerJoinedLobbyEvent(
                                            lobbyCode = payload.lobbyCode,
                                            playerId = PlayerId(1),
                                            playerDisplayName = payload.playerDisplayName,
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")

                var readyLobbyCode: String? = null
                controller.createLobby { code ->
                    readyLobbyCode = code
                }

                waitUntil { controller.state.value.isConnected }
                waitUntil {
                    controller.state.value.sessionToken ==
                        "123e4567-e89b-12d3-a456-426614174200"
                }
                waitUntil { readyLobbyCode == lobbyCode.value }
                waitUntil { controller.state.value.playerNames.contains("Alice") }

                val state = controller.state.value
                assertEquals(lobbyCode.value, state.activeLobbyCode)
                assertTrue(state.isHost)
                assertEquals(listOf("Alice"), state.playerNames)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `join lobby flow should navigate and update player list`() {
        runBlocking {
            val lobbyCode = LobbyCode("Z9Y8")
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174201"),
                        ),
                ) { payload, outgoing ->
                    if (payload is JoinLobbyRequest) {
                        outgoing.send(
                            Frame.Binary(
                                true,
                                MessageCodec.encode(JoinLobbyResponse(payload.lobbyCode)),
                            ),
                        )
                        outgoing.send(
                            Frame.Binary(
                                true,
                                MessageCodec.encode(
                                    PlayerJoinedLobbyEvent(
                                        lobbyCode = payload.lobbyCode,
                                        playerId = PlayerId(2),
                                        playerDisplayName = payload.playerDisplayName,
                                    ),
                                ),
                            ),
                        )
                    }
                }
            val controller = createController(playerNameStore = InMemoryPlayerNameStore("Bob"))
            try {
                controller.updateServerUrl(server.url)
                controller.updateLobbyCode(lobbyCode.value)
                var readyLobbyCode: String? = null
                controller.joinLobby { code ->
                    readyLobbyCode = code
                }

                waitUntil { controller.state.value.isConnected }
                waitUntil {
                    controller.state.value.sessionToken ==
                        "123e4567-e89b-12d3-a456-426614174201"
                }
                waitUntil { readyLobbyCode == lobbyCode.value }
                waitUntil { controller.state.value.playerNames.contains("Bob") }

                val state = controller.state.value
                assertEquals(lobbyCode.value, state.activeLobbyCode)
                assertFalse(state.isHost)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `start game should send backend request and trigger catch up after game started event`() {
        runBlocking {
            val lobbyCode = LobbyCode("S123")
            val seenPayloads = CopyOnWriteArrayList<Any>()
            val server =
                startProtocolServer { payload, outgoing ->
                    seenPayloads += payload
                    when (payload) {
                        CreateLobbyRequest ->
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(CreateLobbyResponse(lobbyCode)),
                                ),
                            )
                        is JoinLobbyRequest -> {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(JoinLobbyResponse(payload.lobbyCode)),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        PlayerJoinedLobbyEvent(
                                            lobbyCode = payload.lobbyCode,
                                            playerId = PlayerId(1),
                                            playerDisplayName = payload.playerDisplayName,
                                            isHost = true,
                                        ),
                                    ),
                                ),
                            )
                        }
                        is StartGameRequest -> {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(StartGameResponse()),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(GameStartedEvent(payload.lobbyCode)),
                                ),
                            )
                        }
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")

                controller.createLobby { }
                waitUntil { controller.state.value.ownPlayerId == PlayerId(1) }

                controller.startGame()
                waitUntil { seenPayloads.any { it is StartGameRequest } }
                waitUntil { controller.state.value.gameStarted }
                waitUntil { seenPayloads.any { it is GameStateCatchUpRequest } }

                assertTrue(controller.state.value.gameState.isCatchingUp)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `player connection lost event should mark lobby member as disconnected`() {
        runBlocking {
            val lobbyCode = LobbyCode("DL42")
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174240"),
                        ),
                ) { payload, outgoing ->
                    if (payload is JoinLobbyRequest) {
                        outgoing.send(
                            Frame.Binary(
                                true,
                                MessageCodec.encode(JoinLobbyResponse(payload.lobbyCode)),
                            ),
                        )
                        outgoing.send(
                            Frame.Binary(
                                true,
                                MessageCodec.encode(
                                    PlayerJoinedLobbyEvent(
                                        lobbyCode = payload.lobbyCode,
                                        playerId = PlayerId(1),
                                        playerDisplayName = payload.playerDisplayName,
                                        isHost = true,
                                    ),
                                ),
                            ),
                        )
                        outgoing.send(
                            Frame.Binary(
                                true,
                                MessageCodec.encode(
                                    PlayerJoinedLobbyEvent(
                                        lobbyCode = payload.lobbyCode,
                                        playerId = PlayerId(2),
                                        playerDisplayName = "Bob",
                                    ),
                                ),
                            ),
                        )
                        outgoing.send(
                            Frame.Binary(
                                true,
                                MessageCodec.encode(
                                    PlayerConnectionLostEvent(
                                        lobbyCode = payload.lobbyCode,
                                        playerId = PlayerId(2),
                                        reason = PlayerConnectionLostReason.SOCKET_CLOSED,
                                    ),
                                ),
                            ),
                        )
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode(lobbyCode.value)

                controller.joinLobby { }

                waitUntil {
                    controller.state.value.players.any { player ->
                        player.playerId == PlayerId(2) && player.isDisconnected
                    }
                }

                val disconnectedPlayer =
                    controller.state.value.players.first { it.playerId == PlayerId(2) }
                assertEquals("Bob", disconnectedPlayer.displayName)
                assertTrue(disconnectedPlayer.isDisconnected)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `refresh game state should request public turn and private snapshots`() {
        runBlocking {
            val lobbyCode = LobbyCode("R123")
            val seenPayloads = CopyOnWriteArrayList<Any>()
            val server =
                startProtocolServer { payload, outgoing ->
                    seenPayloads += payload
                    when (payload) {
                        CreateLobbyRequest ->
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(CreateLobbyResponse(lobbyCode)),
                                ),
                            )
                        is JoinLobbyRequest -> {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(JoinLobbyResponse(payload.lobbyCode)),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        PlayerJoinedLobbyEvent(
                                            lobbyCode = payload.lobbyCode,
                                            playerId = PlayerId(1),
                                            playerDisplayName = payload.playerDisplayName,
                                            isHost = true,
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")

                controller.createLobby { }
                waitUntil { controller.state.value.ownPlayerId == PlayerId(1) }

                controller.refreshGameState()

                waitUntil { seenPayloads.any { it is MapGetRequest } }
                waitUntil { seenPayloads.any { it is TurnStateGetRequest } }
                waitUntil { seenPayloads.any { it is GameStatePrivateGetRequest } }
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `reinforcement actions send backend requests and consume private card updates`() {
        runBlocking {
            val lobbyCode = LobbyCode("RF12")
            val playerId = PlayerId(1)
            val cardIds = listOf(CardId("card-a"), CardId("card-b"), CardId("card-c"))
            val config = LobbyControllerConfig()
            val seenPayloads = CopyOnWriteArrayList<Any>()
            var placeAttempts = 0
            var tradeInAttempts = 0
            var confirmAttempts = 0
            val server =
                startProtocolServer { payload, outgoing ->
                    seenPayloads += payload
                    when (payload) {
                        is JoinLobbyRequest -> {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(JoinLobbyResponse(payload.lobbyCode)),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        PlayerJoinedLobbyEvent(
                                            lobbyCode = lobbyCode,
                                            playerId = playerId,
                                            playerDisplayName = payload.playerDisplayName,
                                        ),
                                    ),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        GameStateCatchUpResponse(
                                            lobbyCode = lobbyCode,
                                            stateVersion = 1,
                                            determinism =
                                                PublicDeterminismMetadataSnapshot(
                                                    mapHash = "hash",
                                                    schemaVersion = 1,
                                                ),
                                            turnState =
                                                PublicTurnStateSnapshot(
                                                    activePlayerId = playerId,
                                                    turnPhase = TurnPhase.REINFORCEMENTS,
                                                    turnCount = 1,
                                                    startPlayerId = playerId,
                                                ),
                                            definition =
                                                MapDefinitionSnapshot(
                                                    territories =
                                                        listOf(
                                                            MapTerritoryDefinitionSnapshot(
                                                                territoryId =
                                                                    TerritoryId(
                                                                        "brasilien",
                                                                    ),
                                                                edges = emptyList(),
                                                            ),
                                                        ),
                                                    continents = emptyList(),
                                                ),
                                            territoryStates =
                                                listOf(
                                                    MapTerritoryStateSnapshot(
                                                        territoryId = TerritoryId("brasilien"),
                                                        ownerId = playerId,
                                                        troopCount = 1,
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        GameStateDeltaEvent(
                                            lobbyCode = lobbyCode,
                                            fromVersion = 1,
                                            toVersion = 2,
                                            events =
                                                listOf(
                                                    ReinforcementsGrantedEvent(
                                                        lobbyCode = lobbyCode,
                                                        playerId = playerId,
                                                        amount = 2,
                                                        territoryBonus = 2,
                                                        continentBonus = 0,
                                                        cardBonus = 0,
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            )
                        }
                        is GameStatePrivateGetRequest ->
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        GameStatePrivateGetResponse(
                                            lobbyCode = lobbyCode,
                                            recipientPlayerId = playerId,
                                            stateVersion = 2,
                                            privateHandCards =
                                                listOf(
                                                    PrivateHandCardSnapshot(cardIds[0], CardType.A),
                                                    PrivateHandCardSnapshot(cardIds[1], CardType.B),
                                                    PrivateHandCardSnapshot(cardIds[2], CardType.C),
                                                ),
                                        ),
                                    ),
                                ),
                            )
                        is PlaceReinforcementsRequest -> {
                            placeAttempts += 1
                            if (placeAttempts == 1) {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            PlaceReinforcementsErrorResponse(
                                                PlaceReinforcementsErrorCode.TERRITORY_NOT_OWNED,
                                                "not owned",
                                            ),
                                        ),
                                    ),
                                )
                            } else {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            GameStateDeltaEvent(
                                                lobbyCode = lobbyCode,
                                                fromVersion = 2,
                                                toVersion = 3,
                                                events =
                                                    listOf(
                                                        PendingReinforcementsChangedEvent(
                                                            lobbyCode = lobbyCode,
                                                            playerId = playerId,
                                                            delta = -2,
                                                        ),
                                                    ),
                                            ),
                                        ),
                                    ),
                                )
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(PlaceReinforcementsResponse(lobbyCode)),
                                    ),
                                )
                            }
                        }
                        is TradeInCardsRequest -> {
                            tradeInAttempts += 1
                            if (tradeInAttempts == 1) {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            TradeInCardsErrorResponse(
                                                TradeInCardsErrorCode.INVALID_SET,
                                                "invalid set",
                                            ),
                                        ),
                                    ),
                                )
                            } else {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(TradeInCardsResponse(lobbyCode)),
                                    ),
                                )
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            PlayerHandUpdatedEvent(
                                                lobbyCode = lobbyCode,
                                                recipientPlayerId = playerId,
                                                stateVersion = 3,
                                                handCards = emptyList(),
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }
                        is ConfirmReinforcementsDoneRequest -> {
                            confirmAttempts += 1
                            if (confirmAttempts == 1) {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            ConfirmReinforcementsDoneErrorResponse(
                                                ConfirmReinforcementsDoneErrorCode
                                                    .PENDING_REINFORCEMENTS_REMAINING,
                                                "pending",
                                            ),
                                        ),
                                    ),
                                )
                            } else {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            ConfirmReinforcementsDoneResponse(lobbyCode),
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode(lobbyCode.value)
                controller.joinLobby { }

                waitUntil { controller.state.value.gameState.reinforcementState.pendingAmount == 2 }
                waitUntil { controller.state.value.gameState.privateHandCards.size == 3 }

                controller.placeReinforcements()
                assertEquals(
                    config.errorReinforcementTargetMissing,
                    controller.state.value.errorText,
                )
                controller.confirmReinforcementsDone()
                assertEquals(config.errorReinforcementsNotAllowed, controller.state.value.errorText)

                controller.selectGameRegion("brazil")
                controller.adjustReinforcementPlacementAmount(1)
                controller.placeReinforcements()
                waitUntil {
                    controller.state.value.errorText ==
                        "Verstärkungen können nur auf eigene Gebiete gesetzt werden."
                }
                controller.placeReinforcements()
                waitUntil { seenPayloads.filterIsInstance<PlaceReinforcementsRequest>().size == 2 }
                val placement = seenPayloads.filterIsInstance<PlaceReinforcementsRequest>().last()
                assertEquals(TerritoryId("brasilien"), placement.placements.single().territoryId)
                assertEquals(2, placement.placements.single().amount)
                waitUntil { controller.state.value.gameState.reinforcementState.pendingAmount == 0 }

                controller.placeReinforcements()
                assertEquals(config.errorReinforcementsNotAllowed, controller.state.value.errorText)
                controller.tradeInCards()
                assertEquals(config.errorTradeInNotAllowed, controller.state.value.errorText)

                cardIds.forEach(controller::toggleTradeInCard)
                controller.tradeInCards()
                waitUntil {
                    controller.state.value.errorText ==
                        "Die gewählten Karten bilden kein gültiges Set."
                }
                controller.tradeInCards()
                waitUntil { seenPayloads.filterIsInstance<TradeInCardsRequest>().size == 2 }
                assertEquals(
                    cardIds.toSet(),
                    seenPayloads.filterIsInstance<TradeInCardsRequest>().last().cardIds.toSet(),
                )
                waitUntil { controller.state.value.gameState.privateHandCards.isEmpty() }

                controller.confirmReinforcementsDone()
                waitUntil {
                    controller.state.value.errorText ==
                        "Verbleibende Verstärkungen müssen zuerst platziert werden."
                }
                controller.confirmReinforcementsDone()
                waitUntil {
                    seenPayloads.filterIsInstance<ConfirmReinforcementsDoneRequest>().size == 2
                }
                waitUntil {
                    !controller.state.value.pendingCommandKeys.contains(
                        LobbyCommandKey.CONFIRM_REINFORCEMENTS_DONE,
                    )
                }
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `reinforcement actions require a local player context`() {
        val config = LobbyControllerConfig()
        val controller = createController(config = config)
        try {
            controller.placeReinforcements()
            assertEquals(config.errorPlayerIdMissing, controller.state.value.errorText)

            controller.confirmReinforcementsDone()
            assertEquals(config.errorPlayerIdMissing, controller.state.value.errorText)

            controller.tradeInCards()
            assertEquals(config.errorPlayerIdMissing, controller.state.value.errorText)

            controller.attack()
            assertEquals(config.errorPlayerIdMissing, controller.state.value.errorText)

            controller.confirmAttackDone()
            assertEquals(config.errorPlayerIdMissing, controller.state.value.errorText)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `attack actions send backend requests and display server battle result`() {
        runBlocking {
            val lobbyCode = LobbyCode("AT12")
            val playerId = PlayerId(1)
            val opponentId = PlayerId(2)
            val config = LobbyControllerConfig()
            val seenPayloads = CopyOnWriteArrayList<Any>()
            var attackAttempts = 0
            var confirmAttempts = 0
            val server =
                startProtocolServer { payload, outgoing ->
                    seenPayloads += payload
                    when (payload) {
                        is JoinLobbyRequest -> {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(JoinLobbyResponse(payload.lobbyCode)),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        PlayerJoinedLobbyEvent(
                                            lobbyCode = lobbyCode,
                                            playerId = playerId,
                                            playerDisplayName = payload.playerDisplayName,
                                        ),
                                    ),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        GameStateCatchUpResponse(
                                            lobbyCode = lobbyCode,
                                            stateVersion = 1,
                                            determinism =
                                                PublicDeterminismMetadataSnapshot(
                                                    mapHash = "hash",
                                                    schemaVersion = 1,
                                                ),
                                            turnState =
                                                PublicTurnStateSnapshot(
                                                    activePlayerId = playerId,
                                                    turnPhase = TurnPhase.ATTACK,
                                                    turnCount = 1,
                                                    startPlayerId = playerId,
                                                ),
                                            definition =
                                                MapDefinitionSnapshot(
                                                    territories =
                                                        listOf(
                                                            MapTerritoryDefinitionSnapshot(
                                                                territoryId =
                                                                    TerritoryId(
                                                                        "brasilien",
                                                                    ),
                                                                edges =
                                                                    listOf(
                                                                        MapTerritoryEdgeSnapshot(
                                                                            TerritoryId(
                                                                                "argentinien",
                                                                            ),
                                                                        ),
                                                                    ),
                                                            ),
                                                            MapTerritoryDefinitionSnapshot(
                                                                territoryId =
                                                                    TerritoryId(
                                                                        "argentinien",
                                                                    ),
                                                                edges = emptyList(),
                                                            ),
                                                        ),
                                                    continents = emptyList(),
                                                ),
                                            territoryStates =
                                                listOf(
                                                    MapTerritoryStateSnapshot(
                                                        territoryId = TerritoryId("brasilien"),
                                                        ownerId = playerId,
                                                        troopCount = 5,
                                                    ),
                                                    MapTerritoryStateSnapshot(
                                                        territoryId = TerritoryId("argentinien"),
                                                        ownerId = opponentId,
                                                        troopCount = 1,
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            )
                        }
                        is AttackRequest -> {
                            attackAttempts += 1
                            if (attackAttempts == 1) {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            AttackErrorResponse(
                                                AttackErrorCode.NOT_ADJACENT,
                                                "not adjacent",
                                            ),
                                        ),
                                    ),
                                )
                            } else {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            GameStateDeltaEvent(
                                                lobbyCode = lobbyCode,
                                                fromVersion = 1,
                                                toVersion = 2,
                                                events =
                                                    listOf(
                                                        AttackResolvedBroadcastEvent(
                                                            lobbyCode = lobbyCode,
                                                            attackerPlayerId = playerId,
                                                            defenderPlayerId = opponentId,
                                                            fromTerritoryId =
                                                                TerritoryId("brasilien"),
                                                            toTerritoryId =
                                                                TerritoryId("argentinien"),
                                                            attackTroops = 3,
                                                            sourceTroopsBefore = 5,
                                                            targetTroopsBefore = 1,
                                                            requestedAttackDice = 3,
                                                            attackDice = 2,
                                                            defendDice = 1,
                                                            attackerRolls = listOf(6, 4),
                                                            defenderRolls = listOf(2),
                                                            attackerLosses = 0,
                                                            defenderLosses = 1,
                                                            attackerRemaining = 3,
                                                            defenderRemaining = 0,
                                                            occupyingTroopCount = 2,
                                                        ),
                                                    ),
                                            ),
                                        ),
                                    ),
                                )
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(AttackResponse(lobbyCode)),
                                    ),
                                )
                            }
                        }
                        is ConfirmAttackDoneRequest -> {
                            confirmAttempts += 1
                            if (confirmAttempts == 1) {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            ConfirmAttackDoneErrorResponse(
                                                ConfirmAttackDoneErrorCode.PHASE_MISMATCH,
                                                "phase",
                                            ),
                                        ),
                                    ),
                                )
                            } else {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(ConfirmAttackDoneResponse(lobbyCode)),
                                    ),
                                )
                            }
                        }
                    }
                }
            val controller = createController(config = config)
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode(lobbyCode.value)
                controller.joinLobby { }

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.ATTACK }
                controller.attack()
                assertEquals(config.errorAttackSelectionMissing, controller.state.value.errorText)

                controller.selectGameRegion("brazil")
                controller.selectGameRegion("argentina")
                controller.adjustAttackTroops(1)
                controller.adjustMoveAfterCapture(1)
                controller.adjustMoveAfterCapture(-1)
                controller.attack()
                waitUntil {
                    controller.state.value.errorText ==
                        "Das Zielgebiet grenzt nicht an das Ausgangsgebiet."
                }
                controller.attack()
                waitUntil { controller.state.value.gameState.attackState.latestResult != null }

                val request = seenPayloads.filterIsInstance<AttackRequest>().last()
                assertEquals(TerritoryId("brasilien"), request.fromTerritoryId)
                assertEquals(TerritoryId("argentinien"), request.toTerritoryId)
                assertEquals(3, request.attackTroops)
                assertEquals(3, request.moveAfterCapture)
                assertTrue(
                    controller.state.value.gameState.attackState.latestResult?.captured == true,
                )

                controller.confirmAttackDone()
                waitUntil {
                    controller.state.value.errorText == "Die Angriffsphase ist bereits beendet."
                }
                controller.confirmAttackDone()
                waitUntil {
                    seenPayloads.filterIsInstance<ConfirmAttackDoneRequest>().size == 2
                }
                waitUntil {
                    LobbyCommandKey.CONFIRM_ATTACK_DONE !in
                        controller.state.value.pendingCommandKeys
                }
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `reconnect should reuse old session token and request catch up`() {
        runBlocking {
            val lobbyCode = LobbyCode("RC01")
            val originalToken = SessionToken("123e4567-e89b-12d3-a456-426614174202")
            val replacementToken = SessionToken("123e4567-e89b-12d3-a456-426614174203")
            val reconnectPayloads = CopyOnWriteArrayList<Any>()
            val firstServer =
                startProtocolServer(
                    onOpenPayload = ConnectionResponse(originalToken),
                ) { payload, outgoing ->
                    when (payload) {
                        CreateLobbyRequest ->
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(CreateLobbyResponse(lobbyCode)),
                                ),
                            )
                        is JoinLobbyRequest -> {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(JoinLobbyResponse(payload.lobbyCode)),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        PlayerJoinedLobbyEvent(
                                            lobbyCode = payload.lobbyCode,
                                            playerId = PlayerId(1),
                                            playerDisplayName = payload.playerDisplayName,
                                            isHost = true,
                                        ),
                                    ),
                                ),
                            )
                        }
                        is StartGameRequest -> {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(StartGameResponse()),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(GameStartedEvent(payload.lobbyCode)),
                                ),
                            )
                        }
                    }
                }
            val controller =
                createController(
                    config =
                        LobbyControllerConfig(
                            reconnectMaxAttempts = 10,
                            reconnectRetryDelayMillis = 100L,
                        ),
                )
            try {
                controller.updateServerUrl(firstServer.url)
                controller.updatePlayerName("Alice")
                controller.createLobby { }
                waitUntil { controller.state.value.ownPlayerId == PlayerId(1) }

                controller.startGame()
                waitUntil { controller.state.value.gameStarted }

                val reconnectPort = firstServer.port
                firstServer.close()
                waitUntil { controller.state.value.isReconnecting }

                val secondServer =
                    startProtocolServerAt(
                        port = reconnectPort,
                        onOpenPayload = ConnectionResponse(replacementToken),
                    ) { payload, outgoing ->
                        reconnectPayloads += payload
                        when (payload) {
                            is ReconnectRequest ->
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            ReconnectResponse(
                                                success = true,
                                                playerId = PlayerId(1),
                                                lobbyCode = lobbyCode,
                                                playerDisplayName = "Alice",
                                            ),
                                        ),
                                    ),
                                )
                        }
                    }

                try {
                    waitUntil {
                        reconnectPayloads.any {
                            it is ReconnectRequest && it.sessionToken == originalToken
                        }
                    }
                    waitUntil {
                        controller.state.value.isConnected &&
                            !controller.state.value.isReconnecting
                    }
                    waitUntil { reconnectPayloads.any { it is GameStateCatchUpRequest } }

                    val state = controller.state.value
                    assertEquals(originalToken.value, state.sessionToken)
                    assertEquals(lobbyCode.value, state.activeLobbyCode)
                    assertEquals(PlayerId(1), state.ownPlayerId)
                } finally {
                    secondServer.close()
                }
            } finally {
                controller.close()
                firstServer.close()
            }
        }
    }

    @Test
    fun `manual connect with active lobby session should reconnect`() {
        runBlocking {
            val lobbyCode = LobbyCode("MR01")
            val originalToken = SessionToken("123e4567-e89b-12d3-a456-426614174212")
            val payloads = CopyOnWriteArrayList<Any>()
            val server =
                startProtocolServer(
                    onOpenPayload = ConnectionResponse(originalToken),
                ) { payload, outgoing ->
                    payloads += payload
                    when (payload) {
                        CreateLobbyRequest ->
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(CreateLobbyResponse(lobbyCode)),
                                ),
                            )
                        is JoinLobbyRequest -> {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(JoinLobbyResponse(payload.lobbyCode)),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        PlayerJoinedLobbyEvent(
                                            lobbyCode = payload.lobbyCode,
                                            playerId = PlayerId(1),
                                            playerDisplayName = payload.playerDisplayName,
                                            isHost = true,
                                        ),
                                    ),
                                ),
                            )
                        }
                        is ReconnectRequest ->
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        ReconnectResponse(
                                            success = true,
                                            playerId = PlayerId(1),
                                            lobbyCode = lobbyCode,
                                            playerDisplayName = "Alice",
                                        ),
                                    ),
                                ),
                            )
                    }
                }
            val controller =
                createController(
                    config =
                        LobbyControllerConfig(
                            reconnectMaxAttempts = 10,
                            reconnectRetryDelayMillis = 100L,
                        ),
                )
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.createLobby { }
                waitUntil { controller.state.value.activeLobbyCode == lobbyCode.value }

                controller.disconnect()
                waitUntil { !controller.state.value.isConnected }
                payloads.clear()

                controller.connect()

                waitUntil {
                    payloads.any {
                        it is ReconnectRequest && it.sessionToken == originalToken
                    }
                }
                waitUntil {
                    controller.state.value.isConnected &&
                        !controller.state.value.isReconnecting
                }

                val reconnectRequests = payloads.filterIsInstance<ReconnectRequest>()
                assertEquals(listOf(originalToken), reconnectRequests.map { it.sessionToken })
                assertFalse(payloads.any { it is CreateLobbyRequest })
                assertFalse(payloads.any { it is JoinLobbyRequest })
                assertEquals(originalToken.value, controller.state.value.sessionToken)
                assertEquals(lobbyCode.value, controller.state.value.activeLobbyCode)
                assertEquals(PlayerId(1), controller.state.value.ownPlayerId)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `startup reconnect should reuse persisted token`() {
        runBlocking {
            val lobbyCode = LobbyCode("PR35")
            val originalToken = SessionToken("123e4567-e89b-12d3-a456-426614174210")
            val reconnectPayloads = CopyOnWriteArrayList<Any>()
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174211"),
                        ),
                ) { payload, outgoing ->
                    reconnectPayloads += payload
                    if (payload is ReconnectRequest) {
                        outgoing.send(
                            Frame.Binary(
                                true,
                                MessageCodec.encode(
                                    ReconnectResponse(
                                        success = true,
                                        playerId = PlayerId(1),
                                        lobbyCode = lobbyCode,
                                        playerDisplayName = "Alice",
                                    ),
                                ),
                            ),
                        )
                    }
                }
            val store =
                InMemoryReconnectSessionStore(
                    token = originalToken.value,
                    serverUrl = server.url,
                    wasGameStarted = true,
                )

            val controller =
                createController(
                    sessionStore = store,
                    config =
                        LobbyControllerConfig(
                            reconnectMaxAttempts = 10,
                            reconnectRetryDelayMillis = 100L,
                        ),
                )
            try {
                waitUntil {
                    reconnectPayloads.any {
                        it is ReconnectRequest && it.sessionToken == originalToken
                    }
                }
                waitUntil {
                    controller.state.value.isConnected &&
                        !controller.state.value.isReconnecting
                }
                waitUntil { reconnectPayloads.any { it is GameStateCatchUpRequest } }

                val state = controller.state.value
                assertEquals(originalToken.value, state.sessionToken)
                assertEquals(lobbyCode.value, state.activeLobbyCode)
                assertEquals(originalToken.value, store.readSessionToken())
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `failed startup reconnect should clear persisted session token`() {
        runBlocking {
            val originalToken = SessionToken("123e4567-e89b-12d3-a456-426614174220")
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174221"),
                        ),
                ) { payload, outgoing ->
                    if (payload is ReconnectRequest) {
                        outgoing.send(
                            Frame.Binary(
                                true,
                                MessageCodec.encode(
                                    ReconnectResponse(
                                        success = false,
                                        errorCode = ReconnectErrorCode.TOKEN_INVALID,
                                    ),
                                ),
                            ),
                        )
                    }
                }
            val store =
                InMemoryReconnectSessionStore(
                    token = originalToken.value,
                    serverUrl = server.url,
                    wasGameStarted = true,
                )

            val controller =
                createController(
                    sessionStore = store,
                    config =
                        LobbyControllerConfig(
                            reconnectMaxAttempts = 10,
                            reconnectRetryDelayMillis = 100L,
                        ),
                )
            try {
                waitUntil {
                    controller.state.value.errorText == ReconnectErrorCode.TOKEN_INVALID.name &&
                        controller.state.value.sessionToken == null
                }

                assertNull(store.readSessionToken())
                assertFalse(store.readWasGameStarted())
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `leave lobby should clear persisted session token`() {
        runBlocking {
            val lobbyCode = LobbyCode("LV42")
            val originalToken = SessionToken("123e4567-e89b-12d3-a456-426614174230")
            val store = InMemoryReconnectSessionStore()
            val server =
                startProtocolServer(
                    onOpenPayload = ConnectionResponse(originalToken),
                ) { payload, outgoing ->
                    when (payload) {
                        CreateLobbyRequest ->
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(CreateLobbyResponse(lobbyCode)),
                                ),
                            )
                        is JoinLobbyRequest ->
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(JoinLobbyResponse(payload.lobbyCode)),
                                ),
                            )
                    }
                }

            val controller = createController(sessionStore = store)
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.createLobby { }

                waitUntil { controller.state.value.sessionToken == originalToken.value }
                waitUntil { controller.state.value.activeLobbyCode == lobbyCode.value }

                controller.leaveLobby()

                assertNull(store.readSessionToken())
                assertNull(controller.state.value.sessionToken)
                assertNull(controller.state.value.activeLobbyCode)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    private fun createController(
        config: LobbyControllerConfig = LobbyControllerConfig(),
        sessionStore: ReconnectSessionStore = InMemoryReconnectSessionStore(),
        playerNameStore: PlayerNameStore = InMemoryPlayerNameStore(),
    ): LobbyController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return LobbyController(
            scope = scope,
            config = config,
            reconnectSessionStore = sessionStore,
            playerNameStore = playerNameStore,
        )
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    private fun startProtocolServer(
        onOpenPayload: NetworkMessagePayload? = null,
        onPayload: suspend (Any, io.ktor.server.websocket.DefaultWebSocketServerSession) -> Unit,
    ): TestWebSocketServer {
        repeat(5) { attempt ->
            val port = findFreePort()
            val server =
                embeddedServer(Netty, port = port) {
                    install(WebSockets)
                    routing {
                        webSocket("/ws") {
                            if (onOpenPayload != null) {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(onOpenPayload),
                                    ),
                                )
                            }
                            for (frame in incoming) {
                                if (frame is Frame.Binary) {
                                    val payload = MessageCodec.decodePayload(frame.readBytes())
                                    onPayload(payload, this)
                                }
                            }
                        }
                    }
                }

            try {
                server.start(wait = false)
                return TestWebSocketServer(server, port, "ws://127.0.0.1:$port/ws")
            } catch (error: Exception) {
                server.stop(0, 0)
                if (attempt == 4) {
                    throw error
                }
            }
        }
        error("Unable to start test websocket server")
    }

    private fun startProtocolServerAt(
        port: Int,
        onOpenPayload: NetworkMessagePayload? = null,
        onPayload: suspend (Any, io.ktor.server.websocket.DefaultWebSocketServerSession) -> Unit,
    ): TestWebSocketServer {
        val server =
            embeddedServer(Netty, port = port) {
                install(WebSockets)
                routing {
                    webSocket("/ws") {
                        if (onOpenPayload != null) {
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(onOpenPayload),
                                ),
                            )
                        }
                        for (frame in incoming) {
                            if (frame is Frame.Binary) {
                                val payload = MessageCodec.decodePayload(frame.readBytes())
                                onPayload(payload, this)
                            }
                        }
                    }
                }
            }

        server.start(wait = false)
        return TestWebSocketServer(server, port, "ws://127.0.0.1:$port/ws")
    }

    private fun findFreePort(): Int =
        ServerSocket(0).use { socket ->
            socket.localPort
        }

    private class TestWebSocketServer(
        private val engine: ApplicationEngine,
        val port: Int,
        val url: String,
    ) {
        fun close() {
            engine.stop(100, 1_000)
        }
    }

    private class InMemoryReconnectSessionStore(
        private var token: String? = null,
        private var serverUrl: String? = null,
        private var wasGameStarted: Boolean = false,
    ) : ReconnectSessionStore {
        override fun readSessionToken(): String? = token

        override fun saveSessionToken(sessionToken: String) {
            token = sessionToken
        }

        override fun clearSessionToken() {
            token = null
        }

        override fun readServerUrl(): String? = serverUrl

        override fun saveServerUrl(serverUrl: String) {
            this.serverUrl = serverUrl
        }

        override fun readWasGameStarted(): Boolean = wasGameStarted

        override fun saveWasGameStarted(wasGameStarted: Boolean) {
            this.wasGameStarted = wasGameStarted
        }

        override fun clearSession() {
            token = null
            wasGameStarted = false
        }
    }

    private class InMemoryPlayerNameStore(
        private var playerName: String? = null,
    ) : PlayerNameStore {
        override fun readPlayerName(): String? = playerName

        override fun savePlayerName(playerName: String) {
            this.playerName = playerName
        }
    }
}
