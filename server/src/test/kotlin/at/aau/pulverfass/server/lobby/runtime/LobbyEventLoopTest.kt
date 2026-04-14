package at.aau.pulverfass.server.lobby.runtime

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.reducer.InvalidLobbyEventException
import at.aau.pulverfass.shared.lobby.state.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LobbyEventLoopTest {
    @Test
    fun `event loop validates lifecycle and processes events`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyCode = LobbyCode("AB12")
            val loop = LobbyEventLoop(lobbyCode = lobbyCode, scope = scope)

            try {
                assertEquals(lobbyCode, loop.lobbyCode)
                assertFailsWith<IllegalStateException> {
                    loop.submit(SystemTick(lobbyCode, 1))
                }

                loop.start()
                assertFailsWith<IllegalStateException> {
                    loop.start()
                }

                loop.submit(SystemTick(lobbyCode, 1))
                waitUntilProcessed(loop, 1)
                assertEquals(1, loop.currentState().processedEventCount)

                assertFailsWith<IllegalArgumentException> {
                    loop.submit(SystemTick(LobbyCode("CD34"), 1))
                }

                loop.shutdown()
                loop.shutdown()
            } finally {
                loop.shutdown()
                scope.cancel()
            }
        }

    @Test
    fun `event loop propagates reducer failures to caller`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyCode = LobbyCode("EF56")
            val loop = LobbyEventLoop(lobbyCode = lobbyCode, scope = scope)

            try {
                loop.start()
                loop.submit(PlayerJoined(lobbyCode, PlayerId(1)))
                assertFailsWith<InvalidLobbyEventException> {
                    loop.submit(PlayerJoined(lobbyCode, PlayerId(1)))
                }
            } finally {
                loop.shutdown()
                scope.cancel()
            }
        }

    @Test
    fun `event loop validates initial state lobby code`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            assertFailsWith<IllegalArgumentException> {
                LobbyEventLoop(
                    lobbyCode = LobbyCode("GH78"),
                    initialState = GameState.initial(LobbyCode("JK90")),
                    scope = scope,
                )
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `event loop detects cancelled scope as not running`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyCode = LobbyCode("LM12")
            val loop = LobbyEventLoop(lobbyCode = lobbyCode, scope = scope)

            try {
                loop.start()
                scope.cancel()

                assertFailsWith<IllegalStateException> {
                    loop.submit(SystemTick(lobbyCode, 2))
                }
            } finally {
                loop.shutdown()
            }
        }

    private suspend fun waitUntilProcessed(
        loop: LobbyEventLoop,
        expectedCount: Long,
    ) {
        withTimeout(5_000) {
            while (loop.currentState().processedEventCount < expectedCount) {
                delay(5)
            }
        }
    }
}
