package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LobbyCommandDispatcherTest {
    @Test
    fun `safe retry sends command a second time after transport failure`() {
        runBlocking {
            var attempts = 0
            val dispatcher =
                LobbyCommandDispatcher(
                    sendPayload = {
                        attempts++
                        if (attempts == 1) {
                            error("kurzer Transportfehler")
                        }
                    },
                    retryDelayMillis = 0,
                )

            dispatcher.send(
                LobbyCommand(
                    key = LobbyCommandKey.MAP_GET,
                    payload = CreateLobbyRequest,
                    retryPolicy = LobbyRetryPolicy.SAFE_ONCE,
                ),
            )

            assertEquals(2, attempts)
        }
    }

    @Test
    fun `non idempotent command is not retried`() {
        runBlocking {
            var attempts = 0
            val dispatcher =
                LobbyCommandDispatcher(
                    sendPayload = {
                        attempts++
                        error("nicht erneut versuchen")
                    },
                    retryDelayMillis = 0,
                )

            assertFailsWith<IllegalStateException> {
                dispatcher.send(
                    LobbyCommand(
                        key = LobbyCommandKey.TURN_ADVANCE,
                        payload = CreateLobbyRequest,
                    ),
                )
            }
            assertEquals(1, attempts)
        }
    }
}
