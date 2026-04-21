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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class LobbyRuntimeTest {
    @Test
    fun `runtime startet und stoppt kontrolliert`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyCode = LobbyCode("AB12")
            val starts = AtomicInteger(0)
            val stops = AtomicInteger(0)
            val runtime =
                LobbyRuntime(
                    lobbyCode = lobbyCode,
                    scope = scope,
                    hooks =
                        LobbyRuntimeHooks(
                            onStarted = { starts.incrementAndGet() },
                            onStopped = { stops.incrementAndGet() },
                        ),
                )

            try {
                runtime.start()
                runtime.submit(SystemTick(lobbyCode, tick = 1))
                waitUntilProcessed(runtime, expectedCount = 1)

                runtime.shutdown()

                assertEquals(1, starts.get())
                assertEquals(1, stops.get())
                assertThrowsSuspend(IllegalStateException::class.java) {
                    runtime.submit(SystemTick(lobbyCode, tick = 2))
                }
            } finally {
                runtime.shutdown()
                scope.cancel()
            }
        }

    @Test
    fun `events werden an den internen event loop weitergeleitet`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyCode = LobbyCode("CD34")
            val acceptedEvents = AtomicInteger(0)
            val runtime =
                LobbyRuntime(
                    lobbyCode = lobbyCode,
                    scope = scope,
                    hooks =
                        LobbyRuntimeHooks(
                            onEventAccepted = { _, _, _, _ -> acceptedEvents.incrementAndGet() },
                        ),
                )

            try {
                runtime.start()
                runtime.submit(PlayerJoined(lobbyCode, PlayerId(7), "Alice"))
                runtime.submit(PlayerJoined(lobbyCode, PlayerId(8), "Bob"))

                waitUntilProcessed(runtime, expectedCount = 2)
                val snapshot = runtime.currentState()

                assertEquals(2, acceptedEvents.get())
                assertEquals(2, snapshot.players.size)
                assertEquals(PlayerId(7), snapshot.activePlayer)
            } finally {
                runtime.shutdown()
                scope.cancel()
            }
        }

    @Test
    fun `direkter externer state zugriff ist nicht notwendig`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyCode = LobbyCode("EF56")
            val mutablePlayers = mutableListOf(PlayerId(1))
            val mutableOrder = mutableListOf(PlayerId(1))
            val runtime =
                LobbyRuntime(
                    lobbyCode = lobbyCode,
                    initialState =
                        GameState(
                            lobbyCode = lobbyCode,
                            players = mutablePlayers,
                            turnOrder = mutableOrder,
                            activePlayer = PlayerId(1),
                        ),
                    scope = scope,
                )

            try {
                runtime.start()
                mutablePlayers.clear()
                mutableOrder.clear()

                val snapshot = runtime.currentState()
                assertEquals(1, snapshot.players.size)
                assertEquals(1, snapshot.turnOrder.size)
                assertTrue(snapshot.activePlayer != null)
            } finally {
                runtime.shutdown()
                scope.cancel()
            }
        }

    private suspend fun waitUntilProcessed(
        runtime: LobbyRuntime,
        expectedCount: Long,
    ) {
        withTimeout(5_000) {
            while (runtime.currentState().processedEventCount < expectedCount) {
                delay(5)
            }
        }
    }

    @Test
    fun `runtime meldet ablehnung ueber hooks und verhindert doppelten start`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyCode = LobbyCode("GH78")
            var rejected = 0
            val runtime =
                LobbyRuntime(
                    lobbyCode = lobbyCode,
                    scope = scope,
                    hooks =
                        LobbyRuntimeHooks(
                            onEventRejected = { _, _, _ -> rejected++ },
                        ),
                )

            try {
                assertEquals(lobbyCode, runtime.lobbyCode)
                runtime.start()
                assertThrows(IllegalStateException::class.java) { runtime.start() }
                runtime.submit(PlayerJoined(lobbyCode, PlayerId(1), "Alice"))

                assertThrowsSuspend(InvalidLobbyEventException::class.java) {
                    runtime.submit(PlayerJoined(lobbyCode, PlayerId(1), "Alice"))
                }
                assertEquals(1, rejected)
            } finally {
                runtime.shutdown()
                runtime.shutdown()
                scope.cancel()
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
