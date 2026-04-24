package at.aau.pulverfass.server.lobby.runtime

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.event.TurnEnded
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LobbyRuntimeRegistryTest {
    @Test
    fun `start lobby lehnt unpassenden initial state direkt ab`() {
        val registry = LobbyRuntimeRegistry(CoroutineScope(SupervisorJob() + Dispatchers.Default))

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                registry.startLobby(
                    lobbyCode = LobbyCode("NO34"),
                    initialState = GameState.initial(LobbyCode("PQ56")),
                )
            }

        assertTrue(exception.message!!.contains("passt nicht zu Lobby"))
    }

    @Test
    fun `events innerhalb einer lobby werden strikt in reihenfolge verarbeitet`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val registry = LobbyRuntimeRegistry(scope)
            val lobbyCode = LobbyCode("AB12")
            val firstPlayer = PlayerId(1)
            val secondPlayer = PlayerId(2)

            try {
                registry.startLobby(lobbyCode)
                registry.submit(PlayerJoined(lobbyCode, firstPlayer, "Alice"))
                registry.submit(PlayerJoined(lobbyCode, secondPlayer, "Bob"))
                registry.submit(GameStarted(lobbyCode))
                registry.submit(TurnEnded(lobbyCode, firstPlayer))

                waitUntilProcessed(registry, lobbyCode, expectedCount = 4)
                val snapshot = registry.currentState(lobbyCode)
                assertNotNull(snapshot)

                assertEquals(listOf(firstPlayer, secondPlayer), snapshot?.players)
                assertEquals(listOf(firstPlayer, secondPlayer), snapshot?.turnOrder)
                assertEquals(firstPlayer, snapshot?.activePlayer)
                assertEquals(TurnPhase.ATTACK, snapshot?.turnState?.turnPhase)
                assertEquals(GameStatus.RUNNING, snapshot?.status)
                assertEquals(1, snapshot?.turnNumber)
            } finally {
                registry.shutdown()
                scope.cancel()
            }
        }

    @Test
    fun `zwei lobbys können parallel events verarbeiten`() =
        runBlocking {
            val blockedLobby = LobbyCode("CD34")
            val freeLobby = LobbyCode("EF56")
            val enteredBlockedReducer = CountDownLatch(1)
            val releaseBlockedReducer = CountDownLatch(1)
            val reducer =
                BlockingReducer(
                    blockedLobby = blockedLobby,
                    enteredLatch = enteredBlockedReducer,
                    releaseLatch = releaseBlockedReducer,
                )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val registry = LobbyRuntimeRegistry(scope = scope, reducerFactory = { reducer })

            try {
                val blockedRuntime = registry.startLobby(blockedLobby)
                registry.startLobby(freeLobby)

                val blockedSubmit =
                    scope.async {
                        blockedRuntime.submit(SystemTick(blockedLobby, tick = 0))
                    }
                assertTrue(
                    enteredBlockedReducer.await(2, TimeUnit.SECONDS),
                    "Blocked reducer did not start in time.",
                )

                registry.submit(SystemTick(freeLobby, tick = 0))
                waitUntilProcessed(registry, freeLobby, expectedCount = 1)

                releaseBlockedReducer.countDown()
                withTimeout(5_000) { blockedSubmit.await() }
                waitUntilProcessed(registry, blockedLobby, expectedCount = 1)
            } finally {
                releaseBlockedReducer.countDown()
                registry.shutdown()
                scope.cancel()
            }
        }

    @Test
    fun `keine events gehen verloren`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val registry = LobbyRuntimeRegistry(scope)
            val lobbyCode = LobbyCode("GH78")
            val eventCount = 1_000L

            try {
                registry.startLobby(lobbyCode)
                repeat(eventCount.toInt()) { tick ->
                    registry.submit(
                        event = SystemTick(lobbyCode, tick.toLong()),
                        context = EventContext(occurredAtEpochMillis = tick.toLong()),
                    )
                }

                waitUntilProcessed(registry, lobbyCode, expectedCount = eventCount)
                val snapshot = registry.currentState(lobbyCode)
                assertNotNull(snapshot)
                assertEquals(eventCount, snapshot?.processedEventCount)
            } finally {
                registry.shutdown()
                scope.cancel()
            }
        }

    @Test
    fun `stop und shutdown beenden loops kontrolliert`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val registry = LobbyRuntimeRegistry(scope)
            val lobbyCode = LobbyCode("JK90")

            try {
                registry.startLobby(lobbyCode)
                registry.submit(SystemTick(lobbyCode, tick = 1))
                waitUntilProcessed(registry, lobbyCode, expectedCount = 1)

                registry.stopLobby(lobbyCode)
                assertThrowsSuspend(IllegalStateException::class.java) {
                    registry.submit(SystemTick(lobbyCode, tick = 2))
                }

                registry.startLobby(lobbyCode)
                registry.submit(SystemTick(lobbyCode, tick = 3))
                waitUntilProcessed(registry, lobbyCode, expectedCount = 1)
            } finally {
                registry.shutdown()
                scope.cancel()
            }
        }

    private suspend fun waitUntilProcessed(
        registry: LobbyRuntimeRegistry,
        lobbyCode: LobbyCode,
        expectedCount: Long,
    ) {
        withTimeout(5_000) {
            while ((registry.currentState(lobbyCode)?.processedEventCount ?: 0) < expectedCount) {
                delay(5)
            }
        }
    }

    @Test
    fun `registry exposes runtime reader and null snapshots consistently`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val registry = LobbyRuntimeRegistry(scope)
            val lobbyCode = LobbyCode("LM12")

            try {
                assertNull(registry.runtime(lobbyCode))
                assertNull(registry.reader(lobbyCode))
                assertNull(registry.currentState(lobbyCode))

                val runtime = registry.startLobby(lobbyCode)
                assertEquals(runtime, registry.runtime(lobbyCode))
                assertEquals(runtime, registry.reader(lobbyCode))
                assertNotNull(registry.currentState(lobbyCode))

                assertEquals(runtime, registry.startLobby(lobbyCode))

                registry.stopLobby(lobbyCode)
                registry.stopLobby(lobbyCode)

                assertNull(registry.runtime(lobbyCode))
                assertNull(registry.reader(lobbyCode))
                assertNull(registry.currentState(lobbyCode))
            } finally {
                registry.shutdown()
                scope.cancel()
            }
        }

    @Test
    fun `registry validates initial state and missing lobby submissions`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val registry = LobbyRuntimeRegistry(scope)

            try {
                val exception =
                    assertThrows(IllegalArgumentException::class.java) {
                        registry.startLobby(
                            lobbyCode = LobbyCode("NO34"),
                            initialState = GameState.initial(LobbyCode("PQ56")),
                        )
                    }
                assertTrue(exception.message!!.contains("passt nicht zu Lobby"))

                assertThrowsSuspend(IllegalStateException::class.java) {
                    registry.submit(SystemTick(LobbyCode("RS78"), 1))
                }
            } finally {
                registry.shutdown()
                scope.cancel()
            }
        }

    @Test
    fun `registry stop lobby deckt suspendierenden pfad ab`() =
        runBlocking {
            val blockedLobby = LobbyCode("TU12")
            val enteredBlockedReducer = CountDownLatch(1)
            val releaseBlockedReducer = CountDownLatch(1)
            val reducer =
                BlockingReducer(
                    blockedLobby = blockedLobby,
                    enteredLatch = enteredBlockedReducer,
                    releaseLatch = releaseBlockedReducer,
                )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val registry =
                LobbyRuntimeRegistry(
                    scope = scope,
                    reducerFactory = { reducer },
                    queueCapacity = 0,
                )

            try {
                registry.startLobby(blockedLobby)
                val submitJob =
                    scope.async {
                        registry.submit(SystemTick(blockedLobby, tick = 0))
                    }
                assertTrue(enteredBlockedReducer.await(2, TimeUnit.SECONDS))
                withTimeout(5_000) {
                    while (submitJob.isCompleted) {
                        delay(5)
                    }
                }
                delay(20)
                assertFalse(submitJob.isCompleted)

                val stopJob =
                    scope.async {
                        registry.stopLobby(blockedLobby)
                    }
                withTimeout(5_000) {
                    while (stopJob.isCompleted) {
                        delay(5)
                    }
                }
                delay(20)
                assertFalse(stopJob.isCompleted)

                releaseBlockedReducer.countDown()
                withTimeout(5_000) {
                    submitJob.await()
                    stopJob.await()
                }

                assertNull(registry.runtime(blockedLobby))
            } finally {
                releaseBlockedReducer.countDown()
                registry.shutdown()
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

private class BlockingReducer(
    private val blockedLobby: LobbyCode,
    private val enteredLatch: CountDownLatch,
    private val releaseLatch: CountDownLatch,
) : LobbyEventReducer {
    private val delegate = DefaultLobbyEventReducer()

    override fun apply(
        state: GameState,
        event: LobbyEvent,
        context: EventContext?,
    ): GameState {
        if (event.lobbyCode == blockedLobby && event is SystemTick && event.tick == 0L) {
            enteredLatch.countDown()
            check(releaseLatch.await(5, TimeUnit.SECONDS)) {
                "Timed out while waiting to release blocked reducer."
            }
        }
        return delegate.apply(state, event, context)
    }
}
