package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
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

    private fun createController(): LobbyController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return LobbyController(scope = scope)
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
                return TestWebSocketServer(server, "ws://127.0.0.1:$port/ws")
            } catch (error: Exception) {
                server.stop(0, 0)
                if (attempt == 4) {
                    throw error
                }
            }
        }
        error("Unable to start test websocket server")
    }

    private fun findFreePort(): Int =
        ServerSocket(0).use { socket ->
            socket.localPort
        }

    private class TestWebSocketServer(
        private val engine: ApplicationEngine,
        val url: String,
    ) {
        fun close() {
            engine.stop(100, 1_000)
        }
    }
}
