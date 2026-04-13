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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `doppelte erstellung schlaegt definiert fehl`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)
            val lobbyCode = LobbyCode("CD34")

            try {
                manager.createLobby(lobbyCode)

                val exception =
                    assertFailsWith<IllegalStateException> {
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
                assertFailsWith<IllegalStateException> {
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
                manager.createLobby(blockedLobby)
                manager.createLobby(freeLobby)

                manager.submit(SystemTick(blockedLobby, tick = 0))
                assertTrue(
                    enteredBlockedReducer.await(2, TimeUnit.SECONDS),
                    "Blocked reducer did not start in time.",
                )

                manager.submit(SystemTick(freeLobby, tick = 0))
                waitUntilProcessed(manager, freeLobby, expectedCount = 1)

                releaseBlockedReducer.countDown()
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
            while ((manager.getLobby(lobbyCode)?.currentState()?.processedEventCount ?: 0L) < expectedCount) {
                delay(5)
            }
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
