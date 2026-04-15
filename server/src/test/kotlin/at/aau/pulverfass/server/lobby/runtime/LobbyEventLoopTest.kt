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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LobbyEventLoopTest {
    @Test
    fun `event loop validates lifecycle and processes events`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyCode = LobbyCode("AB12")
            val loop = LobbyEventLoop(lobbyCode = lobbyCode, scope = scope)

            try {
                assertEquals(lobbyCode, loop.lobbyCode)
                assertThrowsSuspend(IllegalStateException::class.java) {
                    loop.submit(SystemTick(lobbyCode, 1))
                }

                loop.start()
                assertThrows(IllegalStateException::class.java) {
                    loop.start()
                }

                loop.submit(SystemTick(lobbyCode, 1))
                waitUntilProcessed(loop, 1)
                assertEquals(1, loop.currentState().processedEventCount)

                assertThrowsSuspend(IllegalArgumentException::class.java) {
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
                loop.submit(PlayerJoined(lobbyCode, PlayerId(1), "Alice"))
                assertThrowsSuspend(InvalidLobbyEventException::class.java) {
                    loop.submit(PlayerJoined(lobbyCode, PlayerId(1), "Alice"))
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
            assertThrows(IllegalArgumentException::class.java) {
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

                assertThrowsSuspend(IllegalStateException::class.java) {
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
