package at.aau.pulverfass.server.transport

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.transport.TransportError
import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.util.InternalAPI
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

class ServerWebSocketTransportTest {
    @Test
    fun `send throws for unknown connection`() =
        runBlocking {
            val transport = ServerWebSocketTransport()

            val exception =
                assertThrowsSuspend(IllegalArgumentException::class.java) {
                    transport.send(ConnectionId(99), byteArrayOf(1, 2, 3))
                }

            assertEquals("Unknown connection: ConnectionId(value=99)", exception.message)
        }

    @Test
    fun `send emits transport error when session send fails`() =
        runBlocking {
            val transport = ServerWebSocketTransport()
            val outgoing = Channel<Frame>(capacity = 1).apply { close() }
            val session = FakeWebSocketServerSession(outgoing = outgoing)
            val connectionId = ConnectionId(1)

            transport.onConnected(connectionId, session)

            coroutineScope {
                val errorEventDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<TransportError>().first()
                        }
                    }

                assertThrowsSuspend(ClosedSendChannelException::class.java) {
                    transport.send(connectionId, byteArrayOf(4, 5, 6))
                }

                val errorEvent = errorEventDeferred.await()
                assertEquals(connectionId, errorEvent.connectionId)
                assertTrueIsClosedSend(errorEvent.cause)
            }
        }

    @Test
    fun `send copies bytes before writing to websocket session`() =
        runBlocking {
            val transport = ServerWebSocketTransport()
            val outgoing = Channel<Frame>(capacity = 1)
            val session = FakeWebSocketServerSession(outgoing = outgoing)
            val connectionId = ConnectionId(2)
            val payload = byteArrayOf(7, 8, 9)

            transport.onConnected(connectionId, session)
            transport.send(connectionId, payload)
            payload[0] = 0

            val frame = outgoing.receive()
            require(frame is Frame.Binary)
            assertArrayEquals(byteArrayOf(7, 8, 9), frame.data)
        }

    @Test
    fun `onError allows missing connection id`() =
        runBlocking {
            val transport = ServerWebSocketTransport()
            val cause = IllegalStateException("boom")

            coroutineScope {
                val errorEventDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<TransportError>().first()
                        }
                    }

                transport.onError(connectionId = null, cause = cause)

                val errorEvent = errorEventDeferred.await()
                assertNull(errorEvent.connectionId)
                assertEquals(cause, errorEvent.cause)
            }
        }

    private fun assertTrueIsClosedSend(cause: Throwable) {
        assertEquals(ClosedSendChannelException::class, cause::class)
    }

    private suspend fun <T : Throwable> assertThrowsSuspend(
        expectedType: Class<T>,
        block: suspend () -> Unit,
    ): T {
        return try {
            block()
            throw AssertionError("Expected exception of type ${expectedType.name}.")
        } catch (error: Throwable) {
            if (expectedType.isInstance(error)) {
                expectedType.cast(error)!!
            } else {
                throw error
            }
        }
    }
}

private class FakeWebSocketServerSession(
    override val incoming: ReceiveChannel<Frame> = Channel(),
    override val outgoing: SendChannel<Frame> = Channel(),
) : DefaultWebSocketServerSession {
    override val call: ApplicationCall
        get() = throw UnsupportedOperationException("ApplicationCall is not used in this test")
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override var pingIntervalMillis: Long = -1L
    override var timeoutMillis: Long = 15_000L
    override val closeReason = CompletableDeferred<CloseReason?>(null)
    override val coroutineContext: CoroutineContext = Dispatchers.Unconfined

    override suspend fun flush() = Unit

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun terminate() = Unit

    @OptIn(InternalAPI::class)
    override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) = Unit
}
