package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.app.network.ClientNetwork
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.PacketReceiveException
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import at.aau.pulverfass.shared.network.transport.Connected
import at.aau.pulverfass.shared.network.transport.Disconnected
import at.aau.pulverfass.shared.network.transport.TransportError
import at.aau.pulverfass.shared.network.transport.TransportEvent
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LobbyControllerTest {
    @Test
    fun `default constructor should be usable`() {
        val controller = LobbyController()
        controller.close()
    }

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
            assertNull(state.lastMessageType)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `update methods should mutate corresponding state fields`() {
        val controller = createController()
        try {
            controller.updateServerUrl("ws://localhost:9999/ws")
            controller.updatePlayerName("Alice")
            controller.updateLobbyCode("1234")
            controller.setJoining(isJoining = true)

            val state = controller.state.value
            assertEquals("ws://localhost:9999/ws", state.serverUrl)
            assertEquals("Alice", state.playerName)
            assertEquals("1234", state.lobbyCode)
            assertTrue(state.isJoining)
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
    fun `connect should set failure state when server url is invalid`() {
        runBlocking {
            val controller = createController()
            try {
                controller.updatePlayerName("Alice")
                controller.updateServerUrl("this-is-not-a-valid-websocket-url")
                controller.connect()

                waitUntil {
                    controller.state.value.statusText == "Verbindung fehlgeschlagen"
                }

                val state = controller.state.value
                assertFalse(state.isConnecting)
                assertFalse(state.isConnected)
                assertEquals("Verbindung fehlgeschlagen", state.statusText)
                assertNotNull(state.errorText)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun `connect should succeed and send login request to websocket server`() {
        runBlocking {
            val server = startWebSocketServer()
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.connect()

                waitUntil { controller.state.value.isConnected }

                val loginPacket =
                    withTimeout(5_000) {
                        server.receivedBinary.receive()
                    }

                assertEquals(MessageType.LOGIN_REQUEST, decodeMessageType(loginPacket))
                assertEquals("Verbunden", controller.state.value.statusText)
                assertNull(controller.state.value.errorText)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `create lobby should require active connection`() {
        val controller = createController()
        try {
            var callbackCalled = false

            controller.createLobby {
                callbackCalled = true
            }

            val state = controller.state.value
            assertEquals("Bitte zuerst mit dem Server verbinden", state.errorText)
            assertFalse(callbackCalled)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `join lobby should require active connection`() {
        val controller = createController()
        try {
            var callbackCalled = false

            controller.joinLobby {
                callbackCalled = true
            }

            val state = controller.state.value
            assertEquals("Bitte zuerst mit dem Server verbinden", state.errorText)
            assertFalse(callbackCalled)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `create and join should succeed when websocket session is active`() {
        runBlocking {
            val server = startWebSocketServer()
            val controller = createController()
            try {
                controller.updateServerUrl(server.url)
                controller.updatePlayerName("Alice")
                controller.connect()
                waitUntil { controller.state.value.isConnected }

                withTimeout(5_000) {
                    server.receivedBinary.receive()
                }

                var createCode: String? = null
                controller.createLobby { generatedCode ->
                    createCode = generatedCode
                }
                waitUntil { createCode != null }

                controller.updateLobbyCode("1234")
                var joinCode: String? = null
                controller.joinLobby { code ->
                    joinCode = code
                }
                waitUntil { joinCode == "1234" }

                val createPacket =
                    withTimeout(5_000) {
                        server.receivedBinary.receive()
                    }
                val joinPacket =
                    withTimeout(5_000) {
                        server.receivedBinary.receive()
                    }

                assertEquals(MessageType.GAME_CREATE_REQUEST, decodeMessageType(createPacket))
                assertEquals(MessageType.GAME_JOIN_REQUEST, decodeMessageType(joinPacket))
                assertEquals(4, createCode!!.length)
                assertEquals("1234", joinCode)
            } finally {
                controller.close()
                server.close()
            }
        }
    }

    @Test
    fun `join lobby should validate lobby code length`() {
        runBlocking {
            val controller = createController()
            try {
                emitTransportEvent(controller, Connected(ConnectionId(1)))
                waitUntil { controller.state.value.isConnected }

                controller.updateLobbyCode("12")
                controller.joinLobby {}

                assertEquals("Lobbycode muss 4-stellig sein", controller.state.value.errorText)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun `transport events should update connection state`() {
        runBlocking {
            val controller = createController()
            try {
                emitTransportEvent(controller, Connected(ConnectionId(1)))
                waitUntil { controller.state.value.isConnected }
                assertEquals("Verbunden", controller.state.value.statusText)

                emitTransportEvent(controller, Disconnected(ConnectionId(1)))
                waitUntil { !controller.state.value.isConnected }
                assertEquals("Getrennt", controller.state.value.statusText)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun `transport error should update error state with fallback message`() {
        runBlocking {
            val controller = createController()
            try {
                emitTransportEvent(controller, TransportError(ConnectionId(1), RuntimeException()))

                waitUntil { controller.state.value.statusText == "Verbindungsfehler" }
                val state = controller.state.value
                assertFalse(state.isConnected)
                assertFalse(state.isConnecting)
                assertEquals("Unbekannter Transportfehler", state.errorText)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun `transport error should use throwable message when available`() {
        runBlocking {
            val controller = createController()
            try {
                emitTransportEvent(
                    controller,
                    TransportError(ConnectionId(1), RuntimeException("boom")),
                )

                waitUntil { controller.state.value.statusText == "Verbindungsfehler" }
                assertEquals("boom", controller.state.value.errorText)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun `packet receiver packet event should update last message type`() {
        runBlocking {
            val controller = createController()
            try {
                val packet =
                    ReceivedPacket(
                        connectionId = ConnectionId(1),
                        header = MessageHeader(MessageType.HEARTBEAT),
                        packet =
                            SerializedPacket(
                                headerBytes = byteArrayOf(1),
                                payloadBytes = byteArrayOf(2),
                            ),
                    )

                emitReceivedPacket(controller, packet)
                waitUntil { controller.state.value.lastMessageType == MessageType.HEARTBEAT.name }

                assertEquals(MessageType.HEARTBEAT.name, controller.state.value.lastMessageType)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun `packet receiver error event should update error text`() {
        runBlocking {
            val controller = createController()
            try {
                emitPacketError(
                    controller = controller,
                    error =
                        PacketReceiveException(
                            connectionId = ConnectionId(1),
                            message = "Decode failed",
                            cause = IllegalArgumentException("bad bytes"),
                        ),
                )

                waitUntil { controller.state.value.errorText == "Decode failed" }
                assertEquals("Decode failed", controller.state.value.errorText)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun `create lobby should surface send errors when no active websocket session exists`() {
        runBlocking {
            val controller = createController()
            try {
                controller.updatePlayerName("Alice")
                emitTransportEvent(controller, Connected(ConnectionId(1)))
                waitUntil { controller.state.value.isConnected }

                var callbackCalled = false
                controller.createLobby {
                    callbackCalled = true
                }

                waitUntil { controller.state.value.errorText == "No active websocket session" }
                assertEquals("No active websocket session", controller.state.value.errorText)
                assertFalse(callbackCalled)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun `join lobby should surface send errors when no active websocket session exists`() {
        runBlocking {
            val controller = createController()
            try {
                controller.updatePlayerName("Alice")
                controller.updateLobbyCode("1234")
                emitTransportEvent(controller, Connected(ConnectionId(1)))
                waitUntil { controller.state.value.isConnected }

                var callbackCalled = false
                controller.joinLobby {
                    callbackCalled = true
                }

                waitUntil { controller.state.value.errorText == "No active websocket session" }
                assertEquals("No active websocket session", controller.state.value.errorText)
                assertFalse(callbackCalled)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun `disconnect should be safe when no active websocket session exists`() {
        val controller = createController()
        try {
            controller.disconnect()
            assertFalse(controller.state.value.isConnected)
        } finally {
            controller.close()
        }
    }

    private fun createController(): LobbyController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return LobbyController(scope = scope)
    }

    private fun decodeMessageType(packetBytes: ByteArray): MessageType {
        val buffer = ByteBuffer.wrap(packetBytes).order(ByteOrder.BIG_ENDIAN)
        val headerLength = buffer.int
        val headerBytes = ByteArray(headerLength)
        buffer.get(headerBytes)
        val header = Json.decodeFromString(MessageHeader.serializer(), headerBytes.decodeToString())
        return header.type
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    private fun emitTransportEvent(
        controller: LobbyController,
        event: TransportEvent,
    ) {
        val network = readPrivateField(controller, "network") as ClientNetwork
        val events =
            readPrivateField(
                target = network.transport,
                fieldName = "_events",
            ) as MutableSharedFlow<TransportEvent>

        events.tryEmit(event)
    }

    private fun emitReceivedPacket(
        controller: LobbyController,
        packet: ReceivedPacket,
    ) {
        val network = readPrivateField(controller, "network") as ClientNetwork
        val packets =
            readPrivateField(
                target = network.packetReceiver,
                fieldName = "_packets",
            ) as MutableSharedFlow<ReceivedPacket>

        packets.tryEmit(packet)
    }

    private fun emitPacketError(
        controller: LobbyController,
        error: PacketReceiveException,
    ) {
        val network = readPrivateField(controller, "network") as ClientNetwork
        val errors =
            readPrivateField(
                target = network.packetReceiver,
                fieldName = "_errors",
            ) as MutableSharedFlow<PacketReceiveException>

        errors.tryEmit(error)
    }

    private fun readPrivateField(
        target: Any,
        fieldName: String,
    ): Any? {
        val field =
            target::class.java.getDeclaredField(fieldName).apply {
                isAccessible = true
            }
        return field.get(target)
    }

    private fun startWebSocketServer(): TestWebSocketServer {
        repeat(5) { attempt ->
            val port = findFreePort()
            val receivedBinary = Channel<ByteArray>(Channel.UNLIMITED)
            val server =
                embeddedServer(Netty, port = port) {
                    install(WebSockets)
                    routing {
                        webSocket("/ws") {
                            for (frame in incoming) {
                                if (frame is Frame.Binary) {
                                    receivedBinary.send(frame.readBytes())
                                }
                            }
                        }
                    }
                }

            try {
                server.start(wait = false)
                return TestWebSocketServer(server, "ws://127.0.0.1:$port/ws", receivedBinary)
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
        val receivedBinary: Channel<ByteArray>,
    ) {
        fun close() {
            engine.stop(100, 1_000)
            receivedBinary.close()
        }
    }
}
