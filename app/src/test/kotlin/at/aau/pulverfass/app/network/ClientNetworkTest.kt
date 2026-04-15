package at.aau.pulverfass.app.network

import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClientNetworkTest {
    @Test
    fun `connect should propagate transport failure for invalid server url`() {
        runBlocking {
            val network = createNetwork()
            try {
                assertFailsWith<Exception> {
                    network.connect("not-a-valid-websocket-url")
                }
            } finally {
                network.close()
            }
        }
    }

    @Test
    fun `disconnect should be safe when no active connection exists`() {
        runBlocking {
            val network = createNetwork()
            try {
                network.disconnect("test disconnect")
            } finally {
                network.close()
            }
        }
    }

    @Test
    fun `send payload should fail without active websocket session`() {
        runBlocking {
            val network = createNetwork()
            try {
                val error =
                    assertFailsWith<IllegalStateException> {
                        network.sendPayload(CreateLobbyRequest)
                    }

                assertEquals("No active websocket session", error.message)
            } finally {
                network.close()
            }
        }
    }

    @Test
    fun `close should release transport resources`() {
        val network = createNetwork()
        network.close()
    }

    private fun createNetwork(): ClientNetwork {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return ClientNetwork(scope = scope)
    }
}
