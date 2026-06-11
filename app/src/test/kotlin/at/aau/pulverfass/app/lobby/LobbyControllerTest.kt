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
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectErrorCode
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.CharacterSelectedBroadcast
import at.aau.pulverfass.shared.message.lobby.event.ConnectionStatusUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostReason
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.request.AttackRequest
import at.aau.pulverfass.shared.message.lobby.request.CharacterSelectRequest
import at.aau.pulverfass.shared.message.lobby.request.ClaimCheatReinforcementBonusRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmAttackDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmReinforcementsDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.FortifyMoveRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.TradeInCardsRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.AttackResponse
import at.aau.pulverfass.shared.message.lobby.response.CharacterSelectResponse
import at.aau.pulverfass.shared.message.lobby.response.ClaimCheatReinforcementBonusResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmAttackDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmReinforcementsDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.FortifyMoveResponse
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
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CharacterSelectErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorResponse
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
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prüft den LobbyController als Schnittstelle zwischen UI-State, Netzwerk und Persistenz.
 *
 * Die Tests decken Verbindungsaufbau, Reconnect, Lobbybeitritt, Hostwechsel,
 * Charakterwahl, Spielstart, Map-Sync, Phasenkommandos, Fehlertexte und lokale
 * Stores ab. So bleibt sichtbar, welche Backend-Nachrichten im Frontend welchen
 * Zustand oder welches Kommando auslösen.
 */
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
    fun `controller should restore and persist auto attack preference`() {
        val playerNameStore = InMemoryPlayerNameStore(autoAttackEnabled = true)
        val controller = createController(playerNameStore = playerNameStore)
        try {
            assertTrue(controller.state.value.autoAttackEnabled)
            assertTrue(controller.state.value.gameState.attackState.autoAttack.isEnabled)

            controller.setAutoAttackEnabled(false)

            assertFalse(controller.state.value.autoAttackEnabled)
            assertFalse(controller.state.value.gameState.attackState.autoAttack.isEnabled)
            assertNull(controller.state.value.gameState.attackState.autoAttack.statusText)
            assertFalse(playerNameStore.readAutoAttackEnabled())

            controller.setAutoAttackEnabled(true)

            assertTrue(controller.state.value.autoAttackEnabled)
            assertTrue(controller.state.value.gameState.attackState.autoAttack.isEnabled)
            assertNull(controller.state.value.gameState.attackState.autoAttack.statusText)
            assertTrue(playerNameStore.readAutoAttackEnabled())
            assertNull(controller.state.value.errorText)
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
    fun `connection status updates should synchronize disconnect and reconnect`() {
        runBlocking {
            val lobbyCode = LobbyCode("CS42")
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174242"),
                        ),
                ) { payload, outgoing ->
                    if (payload is JoinLobbyRequest) {
                        outgoing.sendPayload(JoinLobbyResponse(payload.lobbyCode))
                        outgoing.sendPayload(
                            PlayerJoinedLobbyEvent(
                                lobbyCode = payload.lobbyCode,
                                playerId = PlayerId(1),
                                playerDisplayName = payload.playerDisplayName,
                                isHost = true,
                            ),
                        )
                        outgoing.sendPayload(
                            PlayerJoinedLobbyEvent(
                                lobbyCode = payload.lobbyCode,
                                playerId = PlayerId(2),
                                playerDisplayName = "Bob",
                            ),
                        )
                        outgoing.sendPayload(
                            ConnectionStatusUpdateEvent(
                                lobbyCode = payload.lobbyCode,
                                playerId = PlayerId(2),
                                status = ConnectionStatus.DISCONNECTED,
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
                        player.playerId == PlayerId(2) &&
                            player.connectionStatus == ConnectionStatus.DISCONNECTED
                    }
                }

                server.broadcast(
                    ConnectionStatusUpdateEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(2),
                        status = ConnectionStatus.CONNECTED,
                    ),
                )
                waitUntil {
                    controller.state.value.players.any { player ->
                        player.playerId == PlayerId(2) &&
                            player.connectionStatus == ConnectionStatus.CONNECTED
                    }
                }

                val players = controller.state.value.players.associateBy(LobbyPlayerUi::playerId)
                assertEquals(
                    ConnectionStatus.CONNECTED,
                    players.getValue(PlayerId(1)).connectionStatus,
                )
                assertEquals(
                    ConnectionStatus.CONNECTED,
                    players.getValue(PlayerId(2)).connectionStatus,
                )
                assertFalse(players.getValue(PlayerId(2)).isDisconnected)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `player left event should promote the announced new host`() {
        runBlocking {
            val lobbyCode = LobbyCode("HT42")
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174241"),
                        ),
                ) { payload, outgoing ->
                    if (payload is JoinLobbyRequest) {
                        outgoing.sendPayload(JoinLobbyResponse(payload.lobbyCode))
                        outgoing.sendPayload(
                            PlayerJoinedLobbyEvent(
                                lobbyCode = payload.lobbyCode,
                                playerId = PlayerId(1),
                                playerDisplayName = "Alice",
                                isHost = true,
                            ),
                        )
                        outgoing.sendPayload(
                            PlayerJoinedLobbyEvent(
                                lobbyCode = payload.lobbyCode,
                                playerId = PlayerId(2),
                                playerDisplayName = payload.playerDisplayName,
                            ),
                        )
                        outgoing.sendPayload(
                            PlayerLeftLobbyEvent(
                                lobbyCode = payload.lobbyCode,
                                playerId = PlayerId(1),
                                newHost = PlayerId(2),
                            ),
                        )
                    }
                }
            val controller = createController(playerNameStore = InMemoryPlayerNameStore("Bob"))
            try {
                controller.updateServerUrl(server.url)
                controller.updateLobbyCode(lobbyCode.value)

                controller.joinLobby { }

                waitUntil {
                    controller.state.value.players.any { player ->
                        player.playerId == PlayerId(2) && player.isHost
                    }
                }

                val state = controller.state.value
                assertTrue(state.isHost)
                assertEquals(listOf("Bob"), state.playerNames)
                assertTrue(state.players.none { it.playerId == PlayerId(1) })
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
                                                                edges =
                                                                    listOf(
                                                                        MapTerritoryEdgeSnapshot(
                                                                            TerritoryId(
                                                                                "brasilien",
                                                                            ),
                                                                        ),
                                                                    ),
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
                                                    MapTerritoryStateSnapshot(
                                                        territoryId = TerritoryId("argentinien"),
                                                        ownerId = PlayerId(2),
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
                        is ClaimCheatReinforcementBonusRequest ->
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        ClaimCheatReinforcementBonusResponse(lobbyCode),
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
                            outgoing.sendPayload(ConfirmReinforcementsDoneResponse(lobbyCode))
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

                controller.claimCheatReinforcementBonus()
                waitUntil { seenPayloads.any { it is ClaimCheatReinforcementBonusRequest } }

                val cheatRequest =
                    seenPayloads.filterIsInstance<ClaimCheatReinforcementBonusRequest>().single()
                assertEquals(lobbyCode, cheatRequest.lobbyCode)
                assertEquals(playerId, cheatRequest.playerId)
                waitUntil {
                    !controller.state.value.pendingCommandKeys.contains(
                        LobbyCommandKey.CLAIM_CHEAT_REINFORCEMENT_BONUS,
                    )
                }

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
                delay(300)
                assertTrue(
                    seenPayloads.filterIsInstance<ConfirmReinforcementsDoneRequest>().isEmpty(),
                )
                assertNull(controller.state.value.autoPhaseNoticeText)
                waitUntil {
                    seenPayloads.filterIsInstance<ConfirmReinforcementsDoneRequest>().size == 1
                }
                assertEquals(
                    "Keine Verstärkungen mehr verfügbar. Die Verstärkungsphase wird " +
                        "automatisch beendet.",
                    controller.state.value.autoPhaseNoticeText,
                )
                waitUntil {
                    LobbyCommandKey.CONFIRM_REINFORCEMENTS_DONE !in
                        controller.state.value.pendingCommandKeys
                }

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

            controller.claimCheatReinforcementBonus()
            assertEquals(config.errorPlayerIdMissing, controller.state.value.errorText)

            controller.tradeInCards()
            assertEquals(config.errorPlayerIdMissing, controller.state.value.errorText)

            controller.attack()
            assertEquals(config.errorPlayerIdMissing, controller.state.value.errorText)

            controller.confirmAttackDone()
            assertEquals(config.errorPlayerIdMissing, controller.state.value.errorText)

            controller.fortifyMove()
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
                                                        TerritoryTroopsChangedEvent(
                                                            lobbyCode = lobbyCode,
                                                            territoryId = TerritoryId("brasilien"),
                                                            troopCount = 3,
                                                            stateVersion = 2,
                                                        ),
                                                        TerritoryOwnerChangedEvent(
                                                            lobbyCode = lobbyCode,
                                                            territoryId =
                                                                TerritoryId("argentinien"),
                                                            ownerId = playerId,
                                                            stateVersion = 2,
                                                        ),
                                                        TerritoryTroopsChangedEvent(
                                                            lobbyCode = lobbyCode,
                                                            territoryId =
                                                                TerritoryId("argentinien"),
                                                            troopCount = 2,
                                                            stateVersion = 2,
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
                                delay(500)
                                outgoing.sendPayload(
                                    PhaseBoundaryEvent(
                                        lobbyCode = lobbyCode,
                                        stateVersion = 3,
                                        previousPhase = TurnPhase.ATTACK,
                                        nextPhase = TurnPhase.FORTIFY,
                                        activePlayerId = playerId,
                                        turnCount = 1,
                                    ),
                                )
                            }
                        }
                        is ConfirmAttackDoneRequest -> {
                            outgoing.sendPayload(ConfirmAttackDoneResponse(lobbyCode))
                            outgoing.sendPayload(
                                PhaseBoundaryEvent(
                                    lobbyCode = lobbyCode,
                                    stateVersion = 3,
                                    previousPhase = TurnPhase.ATTACK,
                                    nextPhase = TurnPhase.FORTIFY,
                                    activePlayerId = playerId,
                                    turnCount = 1,
                                ),
                            )
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
                delay(300)
                assertTrue(seenPayloads.filterIsInstance<ConfirmAttackDoneRequest>().isEmpty())
                assertNull(controller.state.value.autoPhaseNoticeText)
                waitUntil {
                    controller.state.value.autoPhaseNoticeText ==
                        "Keine Angriffe mehr möglich. Die Angriffsphase wird automatisch beendet."
                }
                assertTrue(seenPayloads.filterIsInstance<ConfirmAttackDoneRequest>().isEmpty())
                controller.clearAutoPhaseNotice()
                assertNull(controller.state.value.autoPhaseNoticeText)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `auto attack toggle arms and continues after attacker loses first battle`() {
        runBlocking {
            val lobbyCode = LobbyCode("AA12")
            val playerId = PlayerId(1)
            val opponentId = PlayerId(2)
            val sourceId = TerritoryId("brasilien")
            val targetId = TerritoryId("argentinien")
            val seenPayloads = CopyOnWriteArrayList<Any>()
            val firstAttackReceived = CompletableDeferred<Unit>()
            val releaseFirstDelta = CompletableDeferred<Unit>()
            var attackCount = 0
            val server =
                startProtocolServer { payload, outgoing ->
                    seenPayloads += payload
                    when (payload) {
                        is JoinLobbyRequest -> {
                            outgoing.sendPayload(JoinLobbyResponse(payload.lobbyCode))
                            outgoing.sendPayload(
                                PlayerJoinedLobbyEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = playerId,
                                    playerDisplayName = payload.playerDisplayName,
                                ),
                            )
                            outgoing.sendPayload(
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
                                                        territoryId = sourceId,
                                                        edges =
                                                            listOf(
                                                                MapTerritoryEdgeSnapshot(
                                                                    targetId,
                                                                ),
                                                            ),
                                                    ),
                                                    MapTerritoryDefinitionSnapshot(
                                                        territoryId = targetId,
                                                        edges = emptyList(),
                                                    ),
                                                ),
                                            continents = emptyList(),
                                        ),
                                    territoryStates =
                                        listOf(
                                            MapTerritoryStateSnapshot(
                                                territoryId = sourceId,
                                                ownerId = playerId,
                                                troopCount = 6,
                                            ),
                                            MapTerritoryStateSnapshot(
                                                territoryId = targetId,
                                                ownerId = opponentId,
                                                troopCount = 2,
                                            ),
                                        ),
                                ),
                            )
                        }
                        is AttackRequest -> {
                            attackCount += 1
                            if (attackCount == 1) {
                                firstAttackReceived.complete(Unit)
                                releaseFirstDelta.await()
                                outgoing.sendPayload(
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
                                                    fromTerritoryId = sourceId,
                                                    toTerritoryId = targetId,
                                                    attackTroops = 3,
                                                    sourceTroopsBefore = 6,
                                                    targetTroopsBefore = 2,
                                                    requestedAttackDice = 3,
                                                    attackDice = 3,
                                                    defendDice = 2,
                                                    attackerRolls = listOf(3, 2, 1),
                                                    defenderRolls = listOf(6, 5),
                                                    attackerLosses = 2,
                                                    defenderLosses = 0,
                                                    attackerRemaining = 4,
                                                    defenderRemaining = 2,
                                                ),
                                                TerritoryTroopsChangedEvent(
                                                    lobbyCode = lobbyCode,
                                                    territoryId = sourceId,
                                                    troopCount = 4,
                                                    stateVersion = 2,
                                                ),
                                                TerritoryTroopsChangedEvent(
                                                    lobbyCode = lobbyCode,
                                                    territoryId = targetId,
                                                    troopCount = 2,
                                                    stateVersion = 2,
                                                ),
                                            ),
                                    ),
                                )
                                delay(100)
                                outgoing.sendPayload(
                                    AttackResponse(lobbyCode, requestId = payload.requestId),
                                )
                            } else {
                                outgoing.sendPayload(
                                    AttackResponse(lobbyCode, requestId = payload.requestId),
                                )
                                outgoing.sendPayload(
                                    GameStateDeltaEvent(
                                        lobbyCode = lobbyCode,
                                        fromVersion = 2,
                                        toVersion = 3,
                                        events =
                                            listOf(
                                                AttackResolvedBroadcastEvent(
                                                    lobbyCode = lobbyCode,
                                                    attackerPlayerId = playerId,
                                                    defenderPlayerId = opponentId,
                                                    fromTerritoryId = sourceId,
                                                    toTerritoryId = targetId,
                                                    attackTroops = 3,
                                                    sourceTroopsBefore = 4,
                                                    targetTroopsBefore = 2,
                                                    requestedAttackDice = 3,
                                                    attackDice = 3,
                                                    defendDice = 2,
                                                    attackerRolls = listOf(6, 5, 3),
                                                    defenderRolls = listOf(2, 1),
                                                    attackerLosses = 0,
                                                    defenderLosses = 2,
                                                    attackerRemaining = 4,
                                                    defenderRemaining = 0,
                                                    occupyingTroopCount = 3,
                                                ),
                                                TerritoryTroopsChangedEvent(
                                                    lobbyCode = lobbyCode,
                                                    territoryId = sourceId,
                                                    troopCount = 1,
                                                    stateVersion = 3,
                                                ),
                                                TerritoryOwnerChangedEvent(
                                                    lobbyCode = lobbyCode,
                                                    territoryId = targetId,
                                                    ownerId = playerId,
                                                    stateVersion = 3,
                                                ),
                                                TerritoryTroopsChangedEvent(
                                                    lobbyCode = lobbyCode,
                                                    territoryId = targetId,
                                                    troopCount = 3,
                                                    stateVersion = 3,
                                                ),
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

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.ATTACK }
                controller.selectGameRegion("brazil")
                controller.selectGameRegion("argentina")
                controller.adjustAttackTroops(1)
                controller.adjustMoveAfterCapture(1)
                controller.setAutoAttackEnabled(true)

                delay(100)
                assertEquals(0, seenPayloads.filterIsInstance<AttackRequest>().size)
                assertTrue(controller.state.value.gameState.attackState.autoAttack.isEnabled)
                assertFalse(controller.state.value.gameState.attackState.autoAttack.isRunning)

                controller.attack()
                firstAttackReceived.await()
                delay(100)
                assertEquals(1, seenPayloads.filterIsInstance<AttackRequest>().size)
                assertTrue(controller.state.value.gameState.attackState.autoAttack.isAwaitingResult)
                assertTrue(LobbyCommandKey.ATTACK !in controller.state.value.pendingCommandKeys)
                val firstResultReleasedAt = System.currentTimeMillis()
                releaseFirstDelta.complete(Unit)
                delay(300)
                assertEquals(1, seenPayloads.filterIsInstance<AttackRequest>().size)
                waitUntil { seenPayloads.filterIsInstance<AttackRequest>().size == 2 }
                assertTrue(System.currentTimeMillis() - firstResultReleasedAt >= 500L)
                waitUntil {
                    controller.state.value.gameState.attackState.autoAttack.statusText ==
                        LobbyControllerConfig().autoAttackStoppedCaptured
                }

                val requests = seenPayloads.filterIsInstance<AttackRequest>()
                assertEquals(2, requests.size)
                assertTrue(requests.all { it.requestId?.startsWith("auto-attack-") == true })
                assertTrue(requests.all { it.attackTroops == 3 })
                assertTrue(requests.all { it.moveAfterCapture == 3 })
                assertTrue(controller.state.value.gameState.attackState.autoAttack.isEnabled)
                assertFalse(controller.state.value.gameState.attackState.autoAttack.isRunning)
                assertTrue(
                    controller.state.value.gameState.attackState.latestResult?.captured == true,
                )
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `auto attack waits after catch up resolves pending battle`() {
        runBlocking {
            val lobbyCode = LobbyCode("AC12")
            val playerId = PlayerId(1)
            val opponentId = PlayerId(2)
            val sourceId = TerritoryId("brasilien")
            val targetId = TerritoryId("argentinien")
            val seenPayloads = CopyOnWriteArrayList<Any>()
            val firstAttackReceived = CompletableDeferred<Unit>()
            val releaseFirstResponse = CompletableDeferred<Unit>()
            var attackCount = 0

            fun publicSnapshot(
                stateVersion: Long,
                sourceTroops: Int,
                targetTroops: Int,
            ) = GameStateCatchUpResponse(
                lobbyCode = lobbyCode,
                stateVersion = stateVersion,
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
                                    territoryId = sourceId,
                                    edges = listOf(MapTerritoryEdgeSnapshot(targetId)),
                                ),
                                MapTerritoryDefinitionSnapshot(
                                    territoryId = targetId,
                                    edges = emptyList(),
                                ),
                            ),
                        continents = emptyList(),
                    ),
                territoryStates =
                    listOf(
                        MapTerritoryStateSnapshot(sourceId, playerId, sourceTroops),
                        MapTerritoryStateSnapshot(targetId, opponentId, targetTroops),
                    ),
            )

            val server =
                startProtocolServer { payload, outgoing ->
                    seenPayloads += payload
                    when (payload) {
                        is JoinLobbyRequest -> {
                            outgoing.sendPayload(JoinLobbyResponse(payload.lobbyCode))
                            outgoing.sendPayload(
                                PlayerJoinedLobbyEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = playerId,
                                    playerDisplayName = payload.playerDisplayName,
                                ),
                            )
                            outgoing.sendPayload(publicSnapshot(1, 6, 2))
                        }
                        is GameStatePrivateGetRequest ->
                            outgoing.sendPayload(
                                GameStatePrivateGetResponse(
                                    lobbyCode = lobbyCode,
                                    recipientPlayerId = playerId,
                                    stateVersion = 1,
                                    privateHandCards = emptyList(),
                                ),
                            )
                        is AttackRequest -> {
                            attackCount += 1
                            if (attackCount == 1) {
                                firstAttackReceived.complete(Unit)
                                releaseFirstResponse.await()
                                outgoing.sendPayload(
                                    AttackResponse(lobbyCode, requestId = payload.requestId),
                                )
                            } else {
                                outgoing.sendPayload(
                                    AttackResponse(lobbyCode, requestId = payload.requestId),
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

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.ATTACK }
                controller.selectGameRegion("brazil")
                controller.selectGameRegion("argentina")
                controller.adjustAttackTroops(1)
                controller.adjustMoveAfterCapture(1)
                controller.setAutoAttackEnabled(true)

                controller.attack()
                firstAttackReceived.await()
                assertEquals(1, seenPayloads.filterIsInstance<AttackRequest>().size)

                server.broadcast(publicSnapshot(2, 4, 2))
                val catchUpCompletedAt = System.currentTimeMillis()
                delay(100)
                releaseFirstResponse.complete(Unit)
                delay(300)
                assertEquals(1, seenPayloads.filterIsInstance<AttackRequest>().size)
                waitUntil { seenPayloads.filterIsInstance<AttackRequest>().size == 2 }
                assertTrue(System.currentTimeMillis() - catchUpCompletedAt >= 500L)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `server auto attack boundary waits for visible result delay`() {
        runBlocking {
            val lobbyCode = LobbyCode("AT02")
            val playerId = PlayerId(1)
            val opponentId = PlayerId(2)
            val server =
                startProtocolServer { payload, outgoing ->
                    when (payload) {
                        is JoinLobbyRequest -> {
                            outgoing.sendPayload(JoinLobbyResponse(payload.lobbyCode))
                            outgoing.sendPayload(
                                PlayerJoinedLobbyEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = playerId,
                                    playerDisplayName = payload.playerDisplayName,
                                ),
                            )
                            outgoing.sendPayload(
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
                                                        territoryId = TerritoryId("brasilien"),
                                                        edges =
                                                            listOf(
                                                                MapTerritoryEdgeSnapshot(
                                                                    TerritoryId("argentinien"),
                                                                ),
                                                            ),
                                                    ),
                                                    MapTerritoryDefinitionSnapshot(
                                                        territoryId = TerritoryId("argentinien"),
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
                            )
                        }
                        is AttackRequest -> {
                            outgoing.sendPayload(
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
                                                fromTerritoryId = TerritoryId("brasilien"),
                                                toTerritoryId = TerritoryId("argentinien"),
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
                                            TerritoryTroopsChangedEvent(
                                                lobbyCode = lobbyCode,
                                                territoryId = TerritoryId("brasilien"),
                                                troopCount = 3,
                                                stateVersion = 2,
                                            ),
                                            TerritoryOwnerChangedEvent(
                                                lobbyCode = lobbyCode,
                                                territoryId = TerritoryId("argentinien"),
                                                ownerId = playerId,
                                                stateVersion = 2,
                                            ),
                                            TerritoryTroopsChangedEvent(
                                                lobbyCode = lobbyCode,
                                                territoryId = TerritoryId("argentinien"),
                                                troopCount = 2,
                                                stateVersion = 2,
                                            ),
                                        ),
                                ),
                            )
                            outgoing.sendPayload(AttackResponse(lobbyCode))
                            outgoing.sendPayload(
                                PhaseBoundaryEvent(
                                    lobbyCode = lobbyCode,
                                    stateVersion = 3,
                                    previousPhase = TurnPhase.ATTACK,
                                    nextPhase = TurnPhase.FORTIFY,
                                    activePlayerId = playerId,
                                    turnCount = 1,
                                ),
                            )
                            outgoing.sendPayload(
                                GameStateDeltaEvent(
                                    lobbyCode = lobbyCode,
                                    fromVersion = 2,
                                    toVersion = 3,
                                    events =
                                        listOf(
                                            TurnStateUpdatedEvent(
                                                lobbyCode = lobbyCode,
                                                activePlayerId = playerId,
                                                turnPhase = TurnPhase.FORTIFY,
                                                turnCount = 1,
                                                startPlayerId = playerId,
                                            ),
                                        ),
                                ),
                            )
                        }
                    }
                }
            val controller = createController()
            val observedPhaseStates =
                CopyOnWriteArrayList<Pair<TurnPhase?, String?>>()
            val observer =
                launch {
                    controller.state.collect { state ->
                        observedPhaseStates.add(
                            state.gameState.turnPhase to state.autoPhaseNoticeText,
                        )
                    }
                }
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode(lobbyCode.value)
                controller.joinLobby { }

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.ATTACK }
                controller.selectGameRegion("brazil")
                controller.selectGameRegion("argentina")
                controller.attack()

                waitUntil { controller.state.value.gameState.attackState.latestResult != null }
                delay(300)
                assertNull(controller.state.value.autoPhaseNoticeText)
                assertEquals(TurnPhase.ATTACK, controller.state.value.gameState.turnPhase)

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.FORTIFY }
                assertFalse(
                    observedPhaseStates.any { (phase, notice) ->
                        phase == TurnPhase.FORTIFY && notice == null
                    },
                )
                assertEquals(
                    "Keine Angriffe mehr möglich. Die Angriffsphase wird automatisch beendet.",
                    controller.state.value.autoPhaseNoticeText,
                )
            } finally {
                observer.cancel()
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `manual attack phase end before auto boundary suppresses delayed notice`() {
        runBlocking {
            val lobbyCode = LobbyCode("AT04")
            val playerId = PlayerId(1)
            val opponentId = PlayerId(2)
            val seenPayloads = CopyOnWriteArrayList<Any>()
            val server =
                startProtocolServer { payload, outgoing ->
                    seenPayloads += payload
                    when (payload) {
                        is JoinLobbyRequest -> {
                            outgoing.sendPayload(JoinLobbyResponse(payload.lobbyCode))
                            outgoing.sendPayload(
                                PlayerJoinedLobbyEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = playerId,
                                    playerDisplayName = payload.playerDisplayName,
                                ),
                            )
                            outgoing.sendPayload(
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
                                                        territoryId = TerritoryId("brasilien"),
                                                        edges =
                                                            listOf(
                                                                MapTerritoryEdgeSnapshot(
                                                                    TerritoryId("argentinien"),
                                                                ),
                                                            ),
                                                    ),
                                                    MapTerritoryDefinitionSnapshot(
                                                        territoryId = TerritoryId("argentinien"),
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
                            )
                        }
                        is AttackRequest -> {
                            outgoing.sendPayload(
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
                                                fromTerritoryId = TerritoryId("brasilien"),
                                                toTerritoryId = TerritoryId("argentinien"),
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
                                            TerritoryTroopsChangedEvent(
                                                lobbyCode = lobbyCode,
                                                territoryId = TerritoryId("brasilien"),
                                                troopCount = 3,
                                                stateVersion = 2,
                                            ),
                                            TerritoryOwnerChangedEvent(
                                                lobbyCode = lobbyCode,
                                                territoryId = TerritoryId("argentinien"),
                                                ownerId = playerId,
                                                stateVersion = 2,
                                            ),
                                            TerritoryTroopsChangedEvent(
                                                lobbyCode = lobbyCode,
                                                territoryId = TerritoryId("argentinien"),
                                                troopCount = 2,
                                                stateVersion = 2,
                                            ),
                                        ),
                                ),
                            )
                            outgoing.sendPayload(AttackResponse(lobbyCode))
                        }
                        is ConfirmAttackDoneRequest -> {
                            outgoing.sendPayload(
                                GameStateDeltaEvent(
                                    lobbyCode = lobbyCode,
                                    fromVersion = 2,
                                    toVersion = 3,
                                    events =
                                        listOf(
                                            TurnStateUpdatedEvent(
                                                lobbyCode = lobbyCode,
                                                activePlayerId = playerId,
                                                turnPhase = TurnPhase.FORTIFY,
                                                turnCount = 1,
                                                startPlayerId = playerId,
                                            ),
                                        ),
                                ),
                            )
                            outgoing.sendPayload(ConfirmAttackDoneResponse(lobbyCode))
                            outgoing.sendPayload(
                                PhaseBoundaryEvent(
                                    lobbyCode = lobbyCode,
                                    stateVersion = 3,
                                    previousPhase = TurnPhase.ATTACK,
                                    nextPhase = TurnPhase.FORTIFY,
                                    activePlayerId = playerId,
                                    turnCount = 1,
                                ),
                            )
                        }
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode(lobbyCode.value)
                controller.joinLobby { }

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.ATTACK }
                controller.selectGameRegion("brazil")
                controller.selectGameRegion("argentina")
                controller.attack()
                waitUntil { controller.state.value.gameState.attackState.latestResult != null }

                controller.confirmAttackDone()
                waitUntil {
                    seenPayloads.filterIsInstance<ConfirmAttackDoneRequest>().isNotEmpty()
                }
                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.FORTIFY }
                delay(2_800)

                assertEquals(1, seenPayloads.filterIsInstance<ConfirmAttackDoneRequest>().size)
                assertNull(controller.state.value.autoPhaseNoticeText)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `manual attack phase end consumes deferred server boundary without stale request`() {
        runBlocking {
            val lobbyCode = LobbyCode("AT05")
            val playerId = PlayerId(1)
            val opponentId = PlayerId(2)
            val seenPayloads = CopyOnWriteArrayList<Any>()
            val server =
                startProtocolServer { payload, outgoing ->
                    seenPayloads += payload
                    when (payload) {
                        is JoinLobbyRequest -> {
                            outgoing.sendPayload(JoinLobbyResponse(payload.lobbyCode))
                            outgoing.sendPayload(
                                PlayerJoinedLobbyEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = playerId,
                                    playerDisplayName = payload.playerDisplayName,
                                ),
                            )
                            outgoing.sendPayload(
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
                                                        territoryId = TerritoryId("brasilien"),
                                                        edges =
                                                            listOf(
                                                                MapTerritoryEdgeSnapshot(
                                                                    TerritoryId("argentinien"),
                                                                ),
                                                            ),
                                                    ),
                                                    MapTerritoryDefinitionSnapshot(
                                                        territoryId = TerritoryId("argentinien"),
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
                            )
                        }
                        is AttackRequest -> {
                            outgoing.sendPayload(
                                GameStateDeltaEvent(
                                    lobbyCode = lobbyCode,
                                    fromVersion = 1,
                                    toVersion = 3,
                                    events =
                                        listOf(
                                            AttackResolvedBroadcastEvent(
                                                lobbyCode = lobbyCode,
                                                attackerPlayerId = playerId,
                                                defenderPlayerId = opponentId,
                                                fromTerritoryId = TerritoryId("brasilien"),
                                                toTerritoryId = TerritoryId("argentinien"),
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
                                            TerritoryTroopsChangedEvent(
                                                lobbyCode = lobbyCode,
                                                territoryId = TerritoryId("brasilien"),
                                                troopCount = 3,
                                                stateVersion = 2,
                                            ),
                                            TerritoryOwnerChangedEvent(
                                                lobbyCode = lobbyCode,
                                                territoryId = TerritoryId("argentinien"),
                                                ownerId = playerId,
                                                stateVersion = 2,
                                            ),
                                            TerritoryTroopsChangedEvent(
                                                lobbyCode = lobbyCode,
                                                territoryId = TerritoryId("argentinien"),
                                                troopCount = 2,
                                                stateVersion = 2,
                                            ),
                                            TurnStateUpdatedEvent(
                                                lobbyCode = lobbyCode,
                                                activePlayerId = playerId,
                                                turnPhase = TurnPhase.FORTIFY,
                                                turnCount = 1,
                                                startPlayerId = playerId,
                                            ),
                                        ),
                                ),
                            )
                            outgoing.sendPayload(AttackResponse(lobbyCode))
                            outgoing.sendPayload(
                                PhaseBoundaryEvent(
                                    lobbyCode = lobbyCode,
                                    stateVersion = 3,
                                    previousPhase = TurnPhase.ATTACK,
                                    nextPhase = TurnPhase.FORTIFY,
                                    activePlayerId = playerId,
                                    turnCount = 1,
                                ),
                            )
                        }
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode(lobbyCode.value)
                controller.joinLobby { }

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.ATTACK }
                controller.selectGameRegion("brazil")
                controller.selectGameRegion("argentina")
                controller.attack()
                waitUntil { controller.state.value.gameState.attackState.latestResult != null }
                delay(100)
                assertEquals(TurnPhase.ATTACK, controller.state.value.gameState.turnPhase)

                controller.confirmAttackDone()
                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.FORTIFY }
                delay(2_800)

                assertTrue(seenPayloads.filterIsInstance<ConfirmAttackDoneRequest>().isEmpty())
                assertNull(controller.state.value.autoPhaseNoticeText)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `server auto attack boundary notice is only shown to attacker`() {
        runBlocking {
            val lobbyCode = LobbyCode("AT03")
            val attackerId = PlayerId(1)
            val observerId = PlayerId(2)
            val server =
                startProtocolServer { payload, outgoing ->
                    if (payload is JoinLobbyRequest) {
                        outgoing.sendPayload(JoinLobbyResponse(payload.lobbyCode))
                        outgoing.sendPayload(
                            PlayerJoinedLobbyEvent(
                                lobbyCode = lobbyCode,
                                playerId = observerId,
                                playerDisplayName = payload.playerDisplayName,
                            ),
                        )
                        outgoing.sendPayload(
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
                                        activePlayerId = attackerId,
                                        turnPhase = TurnPhase.ATTACK,
                                        turnCount = 1,
                                        startPlayerId = attackerId,
                                    ),
                                definition =
                                    MapDefinitionSnapshot(
                                        territories =
                                            listOf(
                                                MapTerritoryDefinitionSnapshot(
                                                    territoryId = TerritoryId("brasilien"),
                                                    edges =
                                                        listOf(
                                                            MapTerritoryEdgeSnapshot(
                                                                TerritoryId("argentinien"),
                                                            ),
                                                        ),
                                                ),
                                                MapTerritoryDefinitionSnapshot(
                                                    territoryId = TerritoryId("argentinien"),
                                                    edges = emptyList(),
                                                ),
                                            ),
                                        continents = emptyList(),
                                    ),
                                territoryStates =
                                    listOf(
                                        MapTerritoryStateSnapshot(
                                            territoryId = TerritoryId("brasilien"),
                                            ownerId = attackerId,
                                            troopCount = 3,
                                        ),
                                        MapTerritoryStateSnapshot(
                                            territoryId = TerritoryId("argentinien"),
                                            ownerId = observerId,
                                            troopCount = 1,
                                        ),
                                    ),
                            ),
                        )
                        outgoing.sendPayload(
                            GameStateDeltaEvent(
                                lobbyCode = lobbyCode,
                                fromVersion = 1,
                                toVersion = 2,
                                events =
                                    listOf(
                                        AttackResolvedBroadcastEvent(
                                            lobbyCode = lobbyCode,
                                            attackerPlayerId = attackerId,
                                            defenderPlayerId = observerId,
                                            fromTerritoryId = TerritoryId("brasilien"),
                                            toTerritoryId = TerritoryId("argentinien"),
                                            attackTroops = 2,
                                            sourceTroopsBefore = 3,
                                            targetTroopsBefore = 1,
                                            requestedAttackDice = 2,
                                            attackDice = 2,
                                            defendDice = 1,
                                            attackerRolls = listOf(6, 4),
                                            defenderRolls = listOf(2),
                                            attackerLosses = 0,
                                            defenderLosses = 1,
                                            attackerRemaining = 2,
                                            defenderRemaining = 0,
                                            occupyingTroopCount = 1,
                                        ),
                                        TerritoryOwnerChangedEvent(
                                            lobbyCode = lobbyCode,
                                            territoryId = TerritoryId("argentinien"),
                                            ownerId = attackerId,
                                            stateVersion = 2,
                                        ),
                                    ),
                            ),
                        )
                        outgoing.sendPayload(
                            PhaseBoundaryEvent(
                                lobbyCode = lobbyCode,
                                stateVersion = 3,
                                previousPhase = TurnPhase.ATTACK,
                                nextPhase = TurnPhase.FORTIFY,
                                activePlayerId = attackerId,
                                turnCount = 1,
                            ),
                        )
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Bob")
                controller.updateLobbyCode(lobbyCode.value)
                controller.joinLobby { }

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.FORTIFY }
                assertNull(controller.state.value.autoPhaseNoticeText)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `fortify action sends backend request and marks move as consumed`() {
        runBlocking {
            val lobbyCode = LobbyCode("FT12")
            val playerId = PlayerId(1)
            val config = LobbyControllerConfig()
            val seenPayloads = Collections.synchronizedList(mutableListOf<Any>())
            var fortifyAttempts = 0
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
                                                    turnPhase = TurnPhase.FORTIFY,
                                                    turnCount = 1,
                                                    startPlayerId = playerId,
                                                ),
                                            definition =
                                                MapDefinitionSnapshot(
                                                    territories =
                                                        listOf(
                                                            MapTerritoryDefinitionSnapshot(
                                                                territoryId =
                                                                    TerritoryId("brasilien"),
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
                                                                    TerritoryId("argentinien"),
                                                                edges =
                                                                    listOf(
                                                                        MapTerritoryEdgeSnapshot(
                                                                            TerritoryId(
                                                                                "brasilien",
                                                                            ),
                                                                        ),
                                                                    ),
                                                            ),
                                                        ),
                                                    continents = emptyList(),
                                                ),
                                            territoryStates =
                                                listOf(
                                                    MapTerritoryStateSnapshot(
                                                        territoryId = TerritoryId("brasilien"),
                                                        ownerId = playerId,
                                                        troopCount = 4,
                                                    ),
                                                    MapTerritoryStateSnapshot(
                                                        territoryId = TerritoryId("argentinien"),
                                                        ownerId = playerId,
                                                        troopCount = 2,
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            )
                        }
                        is FortifyMoveRequest -> {
                            fortifyAttempts += 1
                            if (fortifyAttempts == 1) {
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(
                                            FortifyMoveErrorResponse(
                                                FortifyMoveErrorCode.NO_PATH,
                                                "no path",
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
                                                        TerritoryTroopsChangedEvent(
                                                            lobbyCode = lobbyCode,
                                                            territoryId = TerritoryId("brasilien"),
                                                            troopCount = 2,
                                                            stateVersion = 2,
                                                        ),
                                                        TerritoryTroopsChangedEvent(
                                                            lobbyCode = lobbyCode,
                                                            territoryId =
                                                                TerritoryId("argentinien"),
                                                            troopCount = 4,
                                                            stateVersion = 2,
                                                        ),
                                                    ),
                                            ),
                                        ),
                                    ),
                                )
                                outgoing.send(
                                    Frame.Binary(
                                        true,
                                        MessageCodec.encode(FortifyMoveResponse(lobbyCode)),
                                    ),
                                )
                            }
                        }
                        is TurnAdvanceRequest -> {
                            outgoing.sendPayload(TurnAdvanceResponse(lobbyCode))
                        }
                    }
                }
            val controller = createController(config = config)
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode(lobbyCode.value)
                controller.joinLobby { }

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.FORTIFY }
                controller.fortifyMove()
                assertEquals(config.errorFortifySelectionMissing, controller.state.value.errorText)

                controller.selectGameRegion("brazil")
                controller.selectGameRegion("argentina")
                controller.adjustFortifyTroops(1)
                controller.fortifyMove()
                waitUntil {
                    controller.state.value.errorText ==
                        "Zwischen diesen eigenen Gebieten besteht keine Verbindung."
                }
                controller.fortifyMove()
                waitUntil { controller.state.value.gameState.fortifyState.hasMoved }

                val request = seenPayloads.filterIsInstance<FortifyMoveRequest>().last()
                assertEquals(TerritoryId("brasilien"), request.fromTerritoryId)
                assertEquals(TerritoryId("argentinien"), request.toTerritoryId)
                assertEquals(2, request.troopCount)
                assertEquals(
                    2,
                    controller.state.value.gameState.territoryStates
                        .getValue(TerritoryId("brasilien"))
                        .troopCount,
                )
                assertEquals(
                    4,
                    controller.state.value.gameState.territoryStates
                        .getValue(TerritoryId("argentinien"))
                        .troopCount,
                )
                assertTrue(
                    LobbyCommandKey.FORTIFY_MOVE !in controller.state.value.pendingCommandKeys,
                )
                delay(300)
                assertTrue(seenPayloads.filterIsInstance<TurnAdvanceRequest>().isEmpty())
                assertNull(controller.state.value.autoPhaseNoticeText)
                waitUntil {
                    seenPayloads.filterIsInstance<TurnAdvanceRequest>().isNotEmpty()
                }
                val turnAdvanceRequest = seenPayloads.filterIsInstance<TurnAdvanceRequest>().last()
                assertEquals(lobbyCode, turnAdvanceRequest.lobbyCode)
                assertEquals(playerId, turnAdvanceRequest.playerId)
                assertEquals(TurnPhase.FORTIFY, turnAdvanceRequest.expectedPhase)
                assertEquals(
                    "Truppen wurden verschoben. Die Verschiebephase wird automatisch beendet.",
                    controller.state.value.autoPhaseNoticeText,
                )
                waitUntil {
                    LobbyCommandKey.TURN_ADVANCE !in controller.state.value.pendingCommandKeys
                }

                controller.fortifyMove()
                assertEquals(config.errorFortifySelectionMissing, controller.state.value.errorText)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `fortify phase without available moves is advanced automatically`() {
        runBlocking {
            val lobbyCode = LobbyCode("FT00")
            val playerId = PlayerId(1)
            val seenPayloads = Collections.synchronizedList(mutableListOf<Any>())
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
                                                    turnPhase = TurnPhase.FORTIFY,
                                                    turnCount = 1,
                                                    startPlayerId = playerId,
                                                ),
                                            definition =
                                                MapDefinitionSnapshot(
                                                    territories =
                                                        listOf(
                                                            MapTerritoryDefinitionSnapshot(
                                                                territoryId =
                                                                    TerritoryId("brasilien"),
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
                                                                    TerritoryId("argentinien"),
                                                                edges =
                                                                    listOf(
                                                                        MapTerritoryEdgeSnapshot(
                                                                            TerritoryId(
                                                                                "brasilien",
                                                                            ),
                                                                        ),
                                                                    ),
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
                                                    MapTerritoryStateSnapshot(
                                                        territoryId = TerritoryId("argentinien"),
                                                        ownerId = playerId,
                                                        troopCount = 1,
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            )
                        }
                        is TurnAdvanceRequest -> {
                            outgoing.sendPayload(TurnAdvanceResponse(lobbyCode))
                            outgoing.sendPayload(
                                PhaseBoundaryEvent(
                                    lobbyCode = lobbyCode,
                                    stateVersion = 2,
                                    previousPhase = TurnPhase.FORTIFY,
                                    nextPhase = TurnPhase.REINFORCEMENTS,
                                    activePlayerId = PlayerId(2),
                                    turnCount = 2,
                                ),
                            )
                        }
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode(lobbyCode.value)
                controller.joinLobby { }

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.FORTIFY }
                delay(300)
                assertTrue(seenPayloads.filterIsInstance<TurnAdvanceRequest>().isEmpty())
                assertNull(controller.state.value.autoPhaseNoticeText)
                waitUntil {
                    seenPayloads.filterIsInstance<TurnAdvanceRequest>().isNotEmpty()
                }
                val request = seenPayloads.filterIsInstance<TurnAdvanceRequest>().single()
                assertEquals(lobbyCode, request.lobbyCode)
                assertEquals(playerId, request.playerId)
                assertEquals(TurnPhase.FORTIFY, request.expectedPhase)
                assertEquals(
                    "Keine Truppenverschiebung möglich. Die Verschiebephase wird " +
                        "automatisch beendet.",
                    controller.state.value.autoPhaseNoticeText,
                )
                waitUntil {
                    LobbyCommandKey.TURN_ADVANCE !in controller.state.value.pendingCommandKeys &&
                        controller.state.value.gameState.turnPhase == TurnPhase.REINFORCEMENTS
                }
                controller.clearAutoPhaseNotice()
                assertNull(controller.state.value.autoPhaseNoticeText)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `attack phase without available attacks waits for server auto boundary`() {
        runBlocking {
            val lobbyCode = LobbyCode("AT00")
            val playerId = PlayerId(1)
            val seenPayloads = Collections.synchronizedList(mutableListOf<Any>())
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
                                                                    TerritoryId("brasilien"),
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
                                                                    TerritoryId("argentinien"),
                                                                edges =
                                                                    listOf(
                                                                        MapTerritoryEdgeSnapshot(
                                                                            TerritoryId(
                                                                                "brasilien",
                                                                            ),
                                                                        ),
                                                                    ),
                                                            ),
                                                        ),
                                                    continents = emptyList(),
                                                ),
                                            territoryStates =
                                                listOf(
                                                    MapTerritoryStateSnapshot(
                                                        territoryId = TerritoryId("brasilien"),
                                                        ownerId = playerId,
                                                        troopCount = 2,
                                                    ),
                                                    MapTerritoryStateSnapshot(
                                                        territoryId = TerritoryId("argentinien"),
                                                        ownerId = playerId,
                                                        troopCount = 1,
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            )
                            delay(500)
                            outgoing.sendPayload(
                                PhaseBoundaryEvent(
                                    lobbyCode = lobbyCode,
                                    stateVersion = 2,
                                    previousPhase = TurnPhase.ATTACK,
                                    nextPhase = TurnPhase.FORTIFY,
                                    activePlayerId = playerId,
                                    turnCount = 1,
                                ),
                            )
                        }
                        is ConfirmAttackDoneRequest -> {
                            outgoing.sendPayload(ConfirmAttackDoneResponse(lobbyCode))
                            outgoing.sendPayload(
                                PhaseBoundaryEvent(
                                    lobbyCode = lobbyCode,
                                    stateVersion = 2,
                                    previousPhase = TurnPhase.ATTACK,
                                    nextPhase = TurnPhase.FORTIFY,
                                    activePlayerId = playerId,
                                    turnCount = 1,
                                ),
                            )
                        }
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode(lobbyCode.value)
                controller.joinLobby { }

                waitUntil { controller.state.value.gameState.turnPhase == TurnPhase.ATTACK }
                delay(300)
                assertTrue(seenPayloads.filterIsInstance<ConfirmAttackDoneRequest>().isEmpty())
                assertNull(controller.state.value.autoPhaseNoticeText)
                waitUntil {
                    controller.state.value.autoPhaseNoticeText ==
                        "Keine Angriffe mehr möglich. Die Angriffsphase wird automatisch beendet."
                }
                assertTrue(seenPayloads.filterIsInstance<ConfirmAttackDoneRequest>().isEmpty())
                assertEquals(TurnPhase.FORTIFY, controller.state.value.gameState.turnPhase)
                controller.clearAutoPhaseNotice()
                assertNull(controller.state.value.autoPhaseNoticeText)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `auto phase notices are shown sequentially when attack and fortify are skipped`() {
        runBlocking {
            val lobbyCode = LobbyCode("AQ00")
            val playerId = PlayerId(1)
            val nextPlayerId = PlayerId(2)
            val seenPayloads = Collections.synchronizedList(mutableListOf<Any>())
            val server =
                startProtocolServer { payload, outgoing ->
                    seenPayloads += payload
                    when (payload) {
                        is JoinLobbyRequest -> {
                            outgoing.sendPayload(JoinLobbyResponse(payload.lobbyCode))
                            outgoing.sendPayload(
                                PlayerJoinedLobbyEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = playerId,
                                    playerDisplayName = payload.playerDisplayName,
                                ),
                            )
                            outgoing.sendPayload(
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
                                                        territoryId = TerritoryId("brasilien"),
                                                        edges =
                                                            listOf(
                                                                MapTerritoryEdgeSnapshot(
                                                                    TerritoryId("argentinien"),
                                                                ),
                                                            ),
                                                    ),
                                                    MapTerritoryDefinitionSnapshot(
                                                        territoryId = TerritoryId("argentinien"),
                                                        edges =
                                                            listOf(
                                                                MapTerritoryEdgeSnapshot(
                                                                    TerritoryId("brasilien"),
                                                                ),
                                                            ),
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
                                            MapTerritoryStateSnapshot(
                                                territoryId = TerritoryId("argentinien"),
                                                ownerId = playerId,
                                                troopCount = 1,
                                            ),
                                        ),
                                ),
                            )
                            delay(500)
                            outgoing.sendPayload(
                                PhaseBoundaryEvent(
                                    lobbyCode = lobbyCode,
                                    stateVersion = 2,
                                    previousPhase = TurnPhase.ATTACK,
                                    nextPhase = TurnPhase.FORTIFY,
                                    activePlayerId = playerId,
                                    turnCount = 1,
                                ),
                            )
                        }
                        is ConfirmAttackDoneRequest -> {
                            outgoing.sendPayload(ConfirmAttackDoneResponse(lobbyCode))
                            outgoing.sendPayload(
                                PhaseBoundaryEvent(
                                    lobbyCode = lobbyCode,
                                    stateVersion = 2,
                                    previousPhase = TurnPhase.ATTACK,
                                    nextPhase = TurnPhase.FORTIFY,
                                    activePlayerId = playerId,
                                    turnCount = 1,
                                ),
                            )
                        }
                        is TurnAdvanceRequest -> {
                            outgoing.sendPayload(TurnAdvanceResponse(lobbyCode))
                            outgoing.sendPayload(
                                PhaseBoundaryEvent(
                                    lobbyCode = lobbyCode,
                                    stateVersion = 3,
                                    previousPhase = TurnPhase.FORTIFY,
                                    nextPhase = TurnPhase.REINFORCEMENTS,
                                    activePlayerId = nextPlayerId,
                                    turnCount = 2,
                                ),
                            )
                        }
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode(lobbyCode.value)
                controller.joinLobby { }

                waitUntil {
                    controller.state.value.autoPhaseNoticeText ==
                        "Keine Angriffe mehr möglich. Die Angriffsphase wird automatisch beendet."
                }
                assertTrue(seenPayloads.filterIsInstance<ConfirmAttackDoneRequest>().isEmpty())

                controller.clearAutoPhaseNotice()
                waitUntil {
                    seenPayloads.filterIsInstance<TurnAdvanceRequest>().isNotEmpty()
                }
                assertEquals(
                    "Keine Truppenverschiebung möglich. Die Verschiebephase wird " +
                        "automatisch beendet.",
                    controller.state.value.autoPhaseNoticeText,
                )
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

                val secondServer =
                    startProtocolServer(
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

                controller.updateServerUrl(secondServer.url)
                firstServer.disconnectClients("server restart")

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

    @Test
    fun `leave lobby should clear reconnect session even without active lobby code`() {
        val store = InMemoryReconnectSessionStore()
        val controller = createController(sessionStore = store)
        try {
            store.saveSessionToken("123e4567-e89b-12d3-a456-426614174231")
            store.saveWasGameStarted(true)

            controller.leaveLobby()

            assertNull(store.readSessionToken())
            assertFalse(store.readWasGameStarted())
            assertNull(controller.state.value.activeLobbyCode)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `updateCharacter stores characterId without changing gameplay color`() {
        val controller = createController()
        try {
            val gameplayColor = Color(0xFF123456)
            controller.updatePlayerColor(gameplayColor)
            controller.updateCharacter("warrior")
            assertEquals("warrior", controller.state.value.characterId)
            assertEquals(gameplayColor, controller.state.value.playerColor)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `updateCharacter persists characterId to playerNameStore`() {
        val store = InMemoryPlayerNameStore()
        val controller = createController(playerNameStore = store)
        try {
            controller.updateCharacter("character_04")
            assertEquals("character_04", store.readCharacterId())
        } finally {
            controller.close()
        }
    }

    @Test
    fun `updateCharacter stores unknown character id without deriving a color`() {
        val controller = createController()
        try {
            controller.updateCharacter("nonexistent")
            assertEquals("nonexistent", controller.state.value.characterId)
            assertNull(controller.state.value.playerColor)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `selectCharacter returns early when activeLobbyCode is null`() {
        val controller = createController()
        try {
            controller.selectCharacter("warrior")
            assertNull(controller.state.value.characterId)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `character select response updates characterId in state`() {
        runBlocking {
            val lobbyCode = LobbyCode("CC10")
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174220"),
                        ),
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
                                            playerId = PlayerId(10),
                                            playerDisplayName = payload.playerDisplayName,
                                        ),
                                    ),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        CharacterSelectResponse(
                                            lobbyCode = lobbyCode,
                                            characterId = "warrior",
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
                waitUntil { controller.state.value.characterId == "warrior" }
                assertEquals("warrior", controller.state.value.characterId)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `character select error response sets error and clearCharacterSelectError clears it`() {
        runBlocking {
            val lobbyCode = LobbyCode("CC20")
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174221"),
                        ),
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
                                            playerId = PlayerId(11),
                                            playerDisplayName = payload.playerDisplayName,
                                        ),
                                    ),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        CharacterSelectErrorResponse("Charakter ist vergeben"),
                                    ),
                                ),
                            )
                        }
                    }
                }
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Bob")
                controller.createLobby { }
                waitUntil { controller.state.value.characterSelectError != null }
                assertEquals("Charakter ist vergeben", controller.state.value.characterSelectError)
                controller.clearCharacterSelectError()
                assertNull(controller.state.value.characterSelectError)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `character selected broadcast updates existing player characterId`() {
        runBlocking {
            val lobbyCode = LobbyCode("CC30")
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174222"),
                        ),
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
                                            playerId = PlayerId(12),
                                            playerDisplayName = payload.playerDisplayName,
                                        ),
                                    ),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        CharacterSelectedBroadcast(
                                            lobbyCode = lobbyCode,
                                            playerId = PlayerId(12),
                                            characterId = "character_04",
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
                controller.updatePlayerName("Charlie")
                controller.createLobby { }
                waitUntil {
                    controller.state.value.players.any {
                        it.playerId == PlayerId(12) && it.characterId == "character_04"
                    }
                }
                val player = controller.state.value.players.first { it.playerId == PlayerId(12) }
                assertEquals("character_04", player.characterId)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `character selected broadcast is silently ignored for unknown player id`() {
        runBlocking {
            val lobbyCode = LobbyCode("CC40")
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174223"),
                        ),
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
                                            playerId = PlayerId(13),
                                            playerDisplayName = payload.playerDisplayName,
                                        ),
                                    ),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        CharacterSelectedBroadcast(
                                            lobbyCode = lobbyCode,
                                            playerId = PlayerId(99),
                                            characterId = "character_03",
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
                controller.updatePlayerName("Dana")
                controller.createLobby { }
                waitUntil { controller.state.value.activeLobbyCode == lobbyCode.value }
                waitUntil { controller.state.value.players.isNotEmpty() }
                val players = controller.state.value.players
                assertTrue(players.none { it.playerId == PlayerId(99) })
                assertTrue(players.all { it.characterId == null })
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `saved character is selected automatically after own lobby player is known`() {
        runBlocking {
            val lobbyCode = LobbyCode("CC45")
            val seenPayloads = CopyOnWriteArrayList<Any>()
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174225"),
                        ),
                ) { payload, outgoing ->
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
                                            playerId = PlayerId(15),
                                            playerDisplayName = payload.playerDisplayName,
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
            val playerNameStore =
                InMemoryPlayerNameStore()
                    .also { it.saveCharacterId("character_04") }
            val controller = createController(playerNameStore = playerNameStore)
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Finn")
                controller.createLobby { }
                waitUntil {
                    seenPayloads
                        .filterIsInstance<CharacterSelectRequest>()
                        .any { it.characterId == "character_04" }
                }
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `saved character auto selection falls back when preferred character is taken`() {
        runBlocking {
            val lobbyCode = LobbyCode("CC46")
            val seenPayloads = CopyOnWriteArrayList<Any>()
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174226"),
                        ),
                ) { payload, outgoing ->
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
                                            playerId = PlayerId(21),
                                            playerDisplayName = "Alice",
                                        ),
                                    ),
                                ),
                            )
                            outgoing.send(
                                Frame.Binary(
                                    true,
                                    MessageCodec.encode(
                                        CharacterSelectedBroadcast(
                                            lobbyCode = payload.lobbyCode,
                                            playerId = PlayerId(21),
                                            characterId = "character_04",
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
                                            playerId = PlayerId(22),
                                            playerDisplayName = payload.playerDisplayName,
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
            val playerNameStore =
                InMemoryPlayerNameStore()
                    .also { it.saveCharacterId("character_04") }
            val controller = createController(playerNameStore = playerNameStore)
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Finn")
                controller.createLobby { }
                waitUntil {
                    seenPayloads
                        .filterIsInstance<CharacterSelectRequest>()
                        .any { it.characterId != "character_04" }
                }

                val request = seenPayloads.filterIsInstance<CharacterSelectRequest>().single()
                assertEquals("character_01", request.characterId)
                assertTrue(request.characterId !in setOf("character_04"))
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `selectCharacter sends CharacterSelectRequest when lobby and player are set`() {
        runBlocking {
            val lobbyCode = LobbyCode("CC50")
            val seenPayloads = CopyOnWriteArrayList<Any>()
            val server =
                startProtocolServer(
                    onOpenPayload =
                        ConnectionResponse(
                            SessionToken("123e4567-e89b-12d3-a456-426614174224"),
                        ),
                ) { payload, outgoing ->
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
                                            playerId = PlayerId(14),
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
                controller.updatePlayerName("Eve")
                controller.createLobby { }
                waitUntil { controller.state.value.ownPlayerId != null }
                controller.selectCharacter("character_06")
                waitUntil {
                    seenPayloads
                        .filterIsInstance<CharacterSelectRequest>()
                        .any { it.characterId == "character_06" }
                }
                val request =
                    seenPayloads
                        .filterIsInstance<CharacterSelectRequest>()
                        .last { it.characterId == "character_06" }
                assertEquals("character_06", request.characterId)
                assertEquals(lobbyCode, request.lobbyCode)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    /**
     * Erstellt einen LobbyController mit Test-Scope und austauschbaren Stores.
     *
     * @param config Controller-Konfiguration, vor allem Server-URL und Retry-Verhalten.
     * @param sessionStore Reconnect-Store für gespeicherte Sessiondaten.
     * @param playerNameStore Store für Spielername und Charakter-ID.
     */
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

    /**
     * Wartet auf asynchrone Controller-Zustände aus WebSocket-Callbacks.
     *
     * @param condition Bedingung, die innerhalb des Test-Timeouts wahr werden muss.
     */
    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    /**
     * Startet einen lokalen WebSocket-Protokollserver für Controller-Tests.
     *
     * @param onOpenPayload Optionale erste Nachricht, die direkt nach Verbindungsaufbau
     *     an den Client gesendet wird.
     * @param onPayload Callback für jede vom Client empfangene Payload.
     */
    private fun startProtocolServer(
        onOpenPayload: NetworkMessagePayload? = null,
        onPayload: suspend (Any, DefaultWebSocketServerSession) -> Unit,
    ): TestWebSocketServer {
        val activeSessions =
            CopyOnWriteArrayList<DefaultWebSocketServerSession>()
        val server =
            embeddedServer(Netty, port = 0) {
                install(WebSockets)
                routing {
                    webSocket("/ws") {
                        activeSessions += this
                        try {
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
                        } finally {
                            activeSessions -= this
                        }
                    }
                }
            }

        server.start(wait = false)
        val port =
            runBlocking {
                server.resolvedConnectors().single().port
            }
        return TestWebSocketServer(
            engine = server,
            port = port,
            url = "ws://127.0.0.1:$port/ws",
            activeSessions = activeSessions,
        )
    }

    private suspend fun DefaultWebSocketServerSession.sendPayload(payload: NetworkMessagePayload) {
        outgoing.send(
            Frame.Binary(
                true,
                MessageCodec.encode(payload),
            ),
        )
    }

    /**
     * Hält den lokalen Testserver und seine aktiven Sessions zusammen.
     *
     * @param engine Laufende Ktor-Engine.
     * @param port Dynamisch gewählter lokaler Port.
     * @param url WebSocket-URL, die der Controller verwenden kann.
     * @param activeSessions Aktuelle WebSocket-Sessions für simulierte Disconnects.
     */
    private class TestWebSocketServer(
        private val engine: ApplicationEngine,
        val port: Int,
        val url: String,
        private val activeSessions: CopyOnWriteArrayList<DefaultWebSocketServerSession>,
    ) {
        fun broadcast(payload: NetworkMessagePayload) {
            runBlocking {
                activeSessions.forEach { session ->
                    session.outgoing.send(
                        Frame.Binary(
                            true,
                            MessageCodec.encode(payload),
                        ),
                    )
                }
            }
        }

        fun disconnectClients(message: String = "server disconnect") {
            runBlocking {
                activeSessions.forEach { session ->
                    session.close(CloseReason(CloseReason.Codes.NORMAL, message))
                }
            }
        }

        fun close() {
            disconnectClients("server shutdown")
            engine.stop(100, 1_000)
        }
    }

    /**
     * Kleiner Reconnect-Store ohne Android-SharedPreferences für Controller-Tests.
     *
     * @param token Vorbelegter Session-Token für Reconnect-Szenarien.
     * @param serverUrl Vorbelegte Server-URL für Wiederverbindungen.
     * @param wasGameStarted Gespeicherter Spielstartstatus.
     */
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

    /**
     * Hält Spielername und Charakter-ID rein im Speicher.
     *
     * @param playerName Vorbelegter Spielername für Controller-Initialisierung.
     */
    private class InMemoryPlayerNameStore(
        private var playerName: String? = null,
        private var autoAttackEnabled: Boolean = false,
    ) : PlayerNameStore {
        private var characterId: String? = null

        override fun readPlayerName(): String? = playerName

        override fun savePlayerName(playerName: String) {
            this.playerName = playerName
        }

        override fun readCharacterId(): String? = characterId

        override fun saveCharacterId(characterId: String) {
            this.characterId = characterId
        }

        override fun readAutoAttackEnabled(): Boolean = autoAttackEnabled

        override fun saveAutoAttackEnabled(enabled: Boolean) {
            autoAttackEnabled = enabled
        }
    }
}
