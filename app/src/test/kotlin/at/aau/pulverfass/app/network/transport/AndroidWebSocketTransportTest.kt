package at.aau.pulverfass.app.network.transport

import at.aau.pulverfass.app.network.CLIENT_CONNECTION_ID
import at.aau.pulverfass.shared.network.transport.BinaryMessageReceived
import at.aau.pulverfass.shared.network.transport.Connected
import at.aau.pulverfass.shared.network.transport.Disconnected
import at.aau.pulverfass.shared.network.transport.TransportError
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidWebSocketTransportTest {
    @Test
    fun `send should fail without active websocket session`() {
        runBlocking {
            val transport = createTransport()
            try {
                val exception =
                    assertFailsWith<IllegalStateException> {
                        transport.send(byteArrayOf(1, 2))
                    }
                assertEquals("No active websocket session", exception.message)
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun `connect should emit connected and forward binary messages`() {
        runBlocking {
            val server =
                startWebSocketServer { _, _ ->
                    outgoing.send(Frame.Binary(fin = true, data = byteArrayOf(7, 8, 9)))
                    close(CloseReason(CloseReason.Codes.NORMAL, "done"))
                }
            val transport = createTransport()
            try {
                val connectedDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<Connected>().first()
                        }
                    }
                val binaryDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<BinaryMessageReceived>().first()
                        }
                    }
                val disconnectedDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<Disconnected>().first()
                        }
                    }

                transport.connect(server.url)

                val connected = connectedDeferred.await()
                val binary = binaryDeferred.await()
                val disconnected = disconnectedDeferred.await()

                assertEquals(CLIENT_CONNECTION_ID, connected.connectionId)
                assertEquals(CLIENT_CONNECTION_ID, binary.connectionId)
                assertContentEquals(byteArrayOf(7, 8, 9), binary.bytes)
                assertEquals(CLIENT_CONNECTION_ID, disconnected.connectionId)
            } finally {
                transport.close()
                server.close()
            }
        }
    }

    @Test
    fun `connect should emit transport error for text frame`() {
        runBlocking {
            val server =
                startWebSocketServer { _, _ ->
                    outgoing.send(Frame.Text("text-frame"))
                    close(CloseReason(CloseReason.Codes.NORMAL, "done"))
                }
            val transport = createTransport()
            try {
                val errorDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<TransportError>().first()
                        }
                    }

                transport.connect(server.url)

                val error = errorDeferred.await()
                assertEquals(CLIENT_CONNECTION_ID, error.connectionId)
                assertTrue(error.cause.message!!.contains("Text frames are not supported"))
            } finally {
                transport.close()
                server.close()
            }
        }
    }

    @Test
    fun `send should forward binary payload copy to server`() {
        runBlocking {
            val server = startWebSocketServer()
            val transport = createTransport()
            try {
                transport.connect(server.url)

                val payload = byteArrayOf(1, 2, 3)
                transport.send(payload)
                payload[0] = 0

                val received =
                    withTimeout(5_000) {
                        server.receivedBinary.receive()
                    }
                assertContentEquals(byteArrayOf(1, 2, 3), received)
            } finally {
                transport.close()
                server.close()
            }
        }
    }

    @Test
    fun `disconnect should close websocket with provided reason`() {
        runBlocking {
            val server = startWebSocketServer()
            val transport = createTransport()
            try {
                transport.connect(server.url)
                transport.disconnect("bye")

                val reason =
                    withTimeout(5_000) {
                        server.closeReasons.receive()
                    }
                assertEquals("bye", reason?.message)
            } finally {
                transport.close()
                server.close()
            }
        }
    }

    @Test
    fun `disconnect without reason should use default message`() {
        runBlocking {
            val server = startWebSocketServer()
            val transport = createTransport()
            try {
                transport.connect(server.url)
                transport.disconnect()

                val reason =
                    withTimeout(5_000) {
                        server.closeReasons.receive()
                    }
                assertEquals("Client disconnected", reason?.message)
            } finally {
                transport.close()
                server.close()
            }
        }
    }

    @Test
    fun `disconnect should be safe when no session exists`() {
        runBlocking {
            val transport = createTransport()
            try {
                transport.disconnect("test")
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun `connect should fail for malformed websocket url`() {
        runBlocking {
            val transport = createTransport()
            try {
                assertFailsWith<Exception> {
                    transport.connect("bad-url")
                }
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun `close should be idempotent`() {
        val transport = createTransport()
        transport.close()
        transport.close()
    }

    private fun createTransport(): AndroidWebSocketTransport =
        AndroidWebSocketTransport(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

    private fun startWebSocketServer(
        sessionBlock: suspend io.ktor.server.websocket.DefaultWebSocketServerSession.(
            Channel<ByteArray>,
            Channel<CloseReason?>,
        ) -> Unit = { receivedBinary, closeReasons ->
            try {
                for (frame in incoming) {
                    if (frame is Frame.Binary) {
                        receivedBinary.send(frame.readBytes())
                    }
                }
            } finally {
                closeReasons.trySend(closeReason.await())
            }
        },
    ): TestWebSocketServer {
        repeat(5) { attempt ->
            val port = findFreePort()
            val receivedBinary = Channel<ByteArray>(Channel.UNLIMITED)
            val closeReasons = Channel<CloseReason?>(Channel.UNLIMITED)
            val server =
                embeddedServer(Netty, port = port) {
                    install(WebSockets)
                    routing {
                        webSocket("/ws") {
                            sessionBlock(receivedBinary, closeReasons)
                        }
                    }
                }

            try {
                server.start(wait = false)
                return TestWebSocketServer(
                    engine = server,
                    url = "ws://127.0.0.1:$port/ws",
                    receivedBinary = receivedBinary,
                    closeReasons = closeReasons,
                )
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
        val closeReasons: Channel<CloseReason?>,
    ) {
        fun close() {
            engine.stop(100, 1_000)
            receivedBinary.close()
            closeReasons.close()
        }
    }
}
