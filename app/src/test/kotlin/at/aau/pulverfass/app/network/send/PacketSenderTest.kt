package at.aau.pulverfass.app.network.send

import at.aau.pulverfass.app.network.CLIENT_CONNECTION_ID
import at.aau.pulverfass.app.network.transport.AndroidWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PacketSenderTest {
    @Test
    fun `send should reject unknown connection id`() {
        runBlocking {
            val transport = AndroidWebSocketTransport(createScope())
            val sender = PacketSender(transport)

            try {
                val exception =
                    assertFailsWith<IllegalArgumentException> {
                        sender.send(
                            connectionId = ConnectionId(99),
                            bytes = byteArrayOf(1),
                        )
                    }

                assertEquals(
                    "Android client supports only one active connection",
                    exception.message,
                )
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun `send should delegate to transport and fail without active session`() {
        runBlocking {
            val transport = AndroidWebSocketTransport(createScope())
            val sender = PacketSender(transport)

            try {
                val exception =
                    assertFailsWith<IllegalStateException> {
                        sender.send(
                            connectionId = CLIENT_CONNECTION_ID,
                            bytes = byteArrayOf(1, 2, 3),
                        )
                    }

                assertEquals("No active websocket session", exception.message)
            } finally {
                transport.close()
            }
        }
    }

    private fun createScope(): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined,
        )
}
