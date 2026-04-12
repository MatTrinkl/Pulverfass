package at.aau.pulverfass.app.network.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
}
