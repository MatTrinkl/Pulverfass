package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.SystemTick
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LobbyStateProcessorTest {
    @Test
    fun `aktueller snapshot kann gelesen werden`() {
        val lobbyCode = LobbyCode("AB12")
        val processor = DefaultLobbyStateProcessor(GameState.initial(lobbyCode))
        val playerId = PlayerId(1)

        processor.apply(PlayerJoined(lobbyCode, playerId))
        val snapshot = processor.currentState()

        assertEquals(lobbyCode, snapshot.lobbyCode)
        assertEquals(listOf(playerId), snapshot.players)
        assertEquals(playerId, snapshot.activePlayer)
        assertEquals(1, snapshot.processedEventCount)
    }

    @Test
    fun `lesen veraendert den state nicht`() {
        val firstPlayer = PlayerId(7)
        val secondPlayer = PlayerId(8)
        val initialPlayers = mutableListOf(firstPlayer, secondPlayer)
        val initialTurnOrder = mutableListOf(firstPlayer, secondPlayer)
        val processor =
            DefaultLobbyStateProcessor(
                GameState(
                    lobbyCode = LobbyCode("CD34"),
                    players = initialPlayers,
                    turnOrder = initialTurnOrder,
                    activePlayer = firstPlayer,
                    status = GameStatus.RUNNING,
                ),
            )

        val firstRead = processor.currentState()
        initialPlayers.clear()
        initialTurnOrder.clear()
        val secondRead = processor.currentState()

        assertEquals(firstRead, secondRead)
        assertEquals(listOf(firstPlayer, secondPlayer), secondRead.players)
        assertEquals(listOf(firstPlayer, secondPlayer), secondRead.turnOrder)
        assertEquals(firstPlayer, secondRead.activePlayer)
        assertEquals(GameStatus.RUNNING, secondRead.status)
    }

    @Test
    fun `lesen kollidiert nicht mit eventverarbeitung`() {
        val lobbyCode = LobbyCode("EF56")
        val processor = DefaultLobbyStateProcessor(GameState.initial(lobbyCode))
        val eventCount = 1_000
        val start = CountDownLatch(1)
        val writerFailure = AtomicReference<Throwable?>(null)
        val readerFailure = AtomicReference<Throwable?>(null)

        val writer =
            Thread {
                try {
                    start.await()
                    repeat(eventCount) { tick ->
                        processor.apply(SystemTick(lobbyCode, tick.toLong()))
                    }
                } catch (t: Throwable) {
                    writerFailure.set(t)
                }
            }
        val reader =
            Thread {
                try {
                    start.await()
                    repeat(eventCount * 3) {
                        val snapshot = processor.currentState()
                        assertEquals(lobbyCode, snapshot.lobbyCode)
                        assertTrue(snapshot.processedEventCount >= 0)
                        if (snapshot.activePlayer != null) {
                            assertTrue(snapshot.players.contains(snapshot.activePlayer))
                        }
                    }
                } catch (t: Throwable) {
                    readerFailure.set(t)
                }
            }

        writer.start()
        reader.start()
        start.countDown()
        writer.join(5_000)
        reader.join(5_000)

        assertFalse(writer.isAlive)
        assertFalse(reader.isAlive)
        writerFailure.get()?.let { throw it }
        readerFailure.get()?.let { throw it }

        val finalSnapshot = processor.currentState()
        assertEquals(eventCount.toLong(), finalSnapshot.processedEventCount)
        assertNull(finalSnapshot.lastEventContext)
    }
}
