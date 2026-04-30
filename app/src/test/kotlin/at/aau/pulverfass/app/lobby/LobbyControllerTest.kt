package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
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
import java.util.Collections
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

            assertEquals("ws://10.0.2.2:8080/ws", state.serverUrl)
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
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Bob")
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
            val seenPayloads = Collections.synchronizedList(mutableListOf<Any>())
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
    fun `refresh game state should request public turn and private snapshots`() {
        runBlocking {
            val lobbyCode = LobbyCode("R123")
            val seenPayloads = Collections.synchronizedList(mutableListOf<Any>())
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
    fun `reconnect should reuse old session token and request catch up`() {
        runBlocking {
            val lobbyCode = LobbyCode("RC01")
            val originalToken = SessionToken("123e4567-e89b-12d3-a456-426614174202")
            val replacementToken = SessionToken("123e4567-e89b-12d3-a456-426614174203")
            val reconnectPayloads = Collections.synchronizedList(mutableListOf<Any>())
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

    private fun createController(
        config: LobbyControllerConfig = LobbyControllerConfig(),
    ): LobbyController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return LobbyController(scope = scope, config = config)
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
}
