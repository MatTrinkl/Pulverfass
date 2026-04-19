package at.aau.pulverfass.server.lobby.runtime

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
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

class LobbyManagerTest {
    @Test
    fun `lobby wird erstellt und wiedergefunden`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)
            val lobbyCode = LobbyCode("AB12")

            try {
                val created = manager.createLobby(lobbyCode)
                val lookedUp = manager.getLobby(lobbyCode)

                assertEquals(created, lookedUp)
                assertNotNull(lookedUp)
            } finally {
                manager.shutdownAll()
                scope.cancel()
            }
        }

    @Test
    fun `doppelte erstellung schlägt definiert fehl`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)
            val lobbyCode = LobbyCode("CD34")

            try {
                manager.createLobby(lobbyCode)

                val exception =
                    assertThrows(IllegalStateException::class.java) {
                        manager.createLobby(lobbyCode)
                    }

                assertEquals("Lobby 'CD34' exists already.", exception.message)
            } finally {
                manager.shutdownAll()
                scope.cancel()
            }
        }

    @Test
    fun `shutdown einzelner lobby funktioniert`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)
            val lobbyCode = LobbyCode("EF56")

            try {
                manager.createLobby(lobbyCode)
                manager.submit(SystemTick(lobbyCode, tick = 1))
                waitUntilProcessed(manager, lobbyCode, expectedCount = 1)

                manager.removeLobby(lobbyCode)

                assertNull(manager.getLobby(lobbyCode))
                assertThrowsSuspend(IllegalStateException::class.java) {
                    manager.submit(SystemTick(lobbyCode, tick = 2))
                }
            } finally {
                manager.shutdownAll()
                scope.cancel()
            }
        }

    @Test
    fun `parallele nutzung mehrerer lobbys funktioniert`() =
        runBlocking {
            val blockedLobby = LobbyCode("GH78")
            val freeLobby = LobbyCode("JK90")
            val enteredBlockedReducer = CountDownLatch(1)
            val releaseBlockedReducer = CountDownLatch(1)
            val reducer =
                ManagerBlockingReducer(
                    blockedLobby = blockedLobby,
                    enteredLatch = enteredBlockedReducer,
                    releaseLatch = releaseBlockedReducer,
                )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope = scope, reducerFactory = { reducer })

            try {
                val blockedRuntime = manager.createLobby(blockedLobby)
                manager.createLobby(freeLobby)

                val blockedSubmit =
                    scope.async {
                        blockedRuntime.submit(SystemTick(blockedLobby, tick = 0))
                    }
                assertTrue(
                    enteredBlockedReducer.await(2, TimeUnit.SECONDS),
                    "Blocked reducer did not start in time.",
                )

                manager.submit(SystemTick(freeLobby, tick = 0))
                waitUntilProcessed(manager, freeLobby, expectedCount = 1)

                releaseBlockedReducer.countDown()
                withTimeout(5_000) { blockedSubmit.await() }
                waitUntilProcessed(manager, blockedLobby, expectedCount = 1)
            } finally {
                releaseBlockedReducer.countDown()
                manager.shutdownAll()
                scope.cancel()
            }
        }

    private suspend fun waitUntilProcessed(
        manager: LobbyManager,
        lobbyCode: LobbyCode,
        expectedCount: Long,
    ) {
        withTimeout(5_000) {
            while (
                (manager.getLobby(lobbyCode)?.currentState()?.processedEventCount ?: 0L) <
                expectedCount
            ) {
                delay(5)
            }
        }
    }

    @Test
    fun `ungültiger initial state wird bei create lobby abgelehnt`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)

            try {
                val exception =
                    assertThrows(IllegalArgumentException::class.java) {
                        manager.createLobby(
                            lobbyCode = LobbyCode("LM12"),
                            initialState = GameState.initial(LobbyCode("NO34")),
                        )
                    }

                assertTrue(exception.message!!.contains("passt nicht zu Lobby"))
            } finally {
                manager.shutdownAll()
                scope.cancel()
            }
        }

    @Test
    fun `remove unbekannter lobby code ist harmlos`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)

            try {
                manager.removeLobby(LobbyCode("PQ56"))
                assertNull(manager.getLobby(LobbyCode("PQ56")))
            } finally {
                manager.shutdownAll()
                scope.cancel()
            }
        }

    @Test
    fun `submit auf unbekannter lobby schlägt fehl`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)

            try {
                assertThrowsSuspend(IllegalStateException::class.java) {
                    manager.submit(SystemTick(LobbyCode("RS78"), 1))
                }
            } finally {
                manager.shutdownAll()
                scope.cancel()
            }
        }

    @Test
    fun `manager submit deckt suspendierenden pfad isoliert ab`() =
        runBlocking {
            val blockedLobby = LobbyCode("SU12")
            val enteredBlockedReducer = CountDownLatch(1)
            val releaseBlockedReducer = CountDownLatch(1)
            val reducer =
                ManagerBlockingReducer(
                    blockedLobby = blockedLobby,
                    enteredLatch = enteredBlockedReducer,
                    releaseLatch = releaseBlockedReducer,
                )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager =
                LobbyManager(
                    scope = scope,
                    reducerFactory = { reducer },
                    queueCapacity = 0,
                )

            try {
                manager.createLobby(blockedLobby)
                val submitJob =
                    scope.async {
                        manager.submit(SystemTick(blockedLobby, tick = 0))
                    }

                assertTrue(enteredBlockedReducer.await(2, TimeUnit.SECONDS))
                delay(20)
                assertFalse(submitJob.isCompleted)

                releaseBlockedReducer.countDown()
                withTimeout(5_000) {
                    submitJob.await()
                }
            } finally {
                releaseBlockedReducer.countDown()
                manager.shutdownAll()
                scope.cancel()
            }
        }

    @Test
    fun `manager submit und remove lobby decken suspendierende pfade ab`() =
        runBlocking {
            val blockedLobby = LobbyCode("ST90")
            val enteredBlockedReducer = CountDownLatch(1)
            val releaseBlockedReducer = CountDownLatch(1)
            val reducer =
                ManagerBlockingReducer(
                    blockedLobby = blockedLobby,
                    enteredLatch = enteredBlockedReducer,
                    releaseLatch = releaseBlockedReducer,
                )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager =
                LobbyManager(
                    scope = scope,
                    reducerFactory = { reducer },
                    queueCapacity = 0,
                )

            try {
                manager.createLobby(blockedLobby)
                val submitJob =
                    scope.async {
                        manager.submit(SystemTick(blockedLobby, tick = 0))
                    }
                assertTrue(enteredBlockedReducer.await(2, TimeUnit.SECONDS))
                withTimeout(5_000) {
                    while (submitJob.isCompleted) {
                        delay(5)
                    }
                }
                delay(20)
                assertFalse(submitJob.isCompleted)

                val removeJob =
                    scope.async {
                        manager.removeLobby(blockedLobby)
                    }
                withTimeout(5_000) {
                    while (removeJob.isCompleted) {
                        delay(5)
                    }
                }
                delay(20)
                assertFalse(removeJob.isCompleted)

                releaseBlockedReducer.countDown()
                withTimeout(5_000) {
                    submitJob.await()
                    removeJob.await()
                }

                assertNull(manager.getLobby(blockedLobby))
            } finally {
                releaseBlockedReducer.countDown()
                manager.shutdownAll()
                scope.cancel()
            }
        }
}

private class ManagerBlockingReducer(
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
