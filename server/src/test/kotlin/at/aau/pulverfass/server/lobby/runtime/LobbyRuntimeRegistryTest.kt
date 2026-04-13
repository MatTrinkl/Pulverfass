package at.aau.pulverfass.server.lobby.runtime

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.event.TurnEnded
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
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
import kotlin.test.assertTrue

class LobbyRuntimeRegistryTest {
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
                registry.submit(PlayerJoined(lobbyCode, firstPlayer))
                registry.submit(PlayerJoined(lobbyCode, secondPlayer))
                registry.submit(TurnEnded(lobbyCode, firstPlayer))

                waitUntilProcessed(registry, lobbyCode, expectedCount = 3)
                val snapshot = assertNotNull(registry.currentState(lobbyCode))

                assertEquals(listOf(firstPlayer, secondPlayer), snapshot.players)
                assertEquals(listOf(firstPlayer, secondPlayer), snapshot.turnOrder)
                assertEquals(secondPlayer, snapshot.activePlayer)
                assertEquals(GameStatus.RUNNING, snapshot.status)
                assertEquals(1, snapshot.turnNumber)
            } finally {
                registry.shutdown()
                scope.cancel()
            }
        }

    @Test
    fun `zwei lobbys koennen parallel events verarbeiten`() =
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
                registry.startLobby(blockedLobby)
                registry.startLobby(freeLobby)

                registry.submit(SystemTick(blockedLobby, tick = 0))
                assertTrue(
                    enteredBlockedReducer.await(2, TimeUnit.SECONDS),
                    "Blocked reducer did not start in time.",
                )

                registry.submit(SystemTick(freeLobby, tick = 0))
                waitUntilProcessed(registry, freeLobby, expectedCount = 1)

                releaseBlockedReducer.countDown()
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
                val snapshot = assertNotNull(registry.currentState(lobbyCode))
                assertEquals(eventCount, snapshot.processedEventCount)
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
                assertFailsWith<IllegalStateException> {
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
