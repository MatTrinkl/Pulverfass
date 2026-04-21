package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.reducer.InvalidLobbyEventException
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class LobbyStateProcessorTest {
    @Test
    fun `aktueller snapshot kann gelesen werden`() {
        val lobbyCode = LobbyCode("AB12")
        val processor = DefaultLobbyStateProcessor(GameState.initial(lobbyCode))
        val playerId = PlayerId(1)

        processor.apply(PlayerJoined(lobbyCode, playerId, "Alice"))
        val snapshot = processor.currentState()

        assertEquals(lobbyCode, snapshot.lobbyCode)
        assertEquals(listOf(playerId), snapshot.players)
        assertEquals(playerId, snapshot.activePlayer)
        assertEquals(1, snapshot.stateVersion)
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
                        assertTrue(snapshot.stateVersion >= 0)
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
        assertEquals(eventCount.toLong(), finalSnapshot.stateVersion)
        assertEquals(eventCount.toLong(), finalSnapshot.processedEventCount)
        assertNull(finalSnapshot.lastEventContext)
    }

    @Test
    fun `interface default apply delegates to processor implementation`() {
        val lobbyCode = LobbyCode("HI12")
        val processor: LobbyStateProcessor =
            DefaultLobbyStateProcessor(GameState.initial(lobbyCode))

        val updated = processor.apply(PlayerJoined(lobbyCode, PlayerId(4), "Player 4"))

        assertEquals(1, updated.stateVersion)
        assertEquals(1, updated.processedEventCount)
        assertEquals(PlayerId(4), updated.activePlayer)
    }

    @Test
    fun `processor keeps state unchanged when reducer throws`() {
        val lobbyCode = LobbyCode("IJ34")
        val processor =
            DefaultLobbyStateProcessor(
                GameState.initial(lobbyCode),
                reducer =
                    object : LobbyEventReducer {
                        override fun apply(
                            state: GameState,
                            event: at.aau.pulverfass.shared.lobby.event.LobbyEvent,
                            context: at.aau.pulverfass.shared.event.EventContext?,
                        ): GameState {
                            throw InvalidLobbyEventException("boom")
                        }
                    },
            )

        assertThrows(InvalidLobbyEventException::class.java) {
            processor.apply(SystemTick(lobbyCode, 1))
        }
        assertEquals(GameState.initial(lobbyCode), processor.currentState())
    }

    @Test
    fun `interface default implementation applies null context`() {
        val lobbyCode = LobbyCode("KL56")
        val processor: LobbyStateProcessor =
            DefaultLobbyStateProcessor(GameState.initial(lobbyCode))
        val defaultImplsClass =
            Class.forName(
                "at.aau.pulverfass.shared.lobby.state.LobbyStateProcessor\$DefaultImpls",
            )
        val method =
            defaultImplsClass.getDeclaredMethod(
                "apply\$default",
                LobbyStateProcessor::class.java,
                at.aau.pulverfass.shared.lobby.event.LobbyEvent::class.java,
                at.aau.pulverfass.shared.event.EventContext::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )

        val updated =
            method.invoke(
                null,
                processor,
                PlayerJoined(lobbyCode, PlayerId(5), "Player 5"),
                null,
                2,
                null,
            ) as GameState

        assertEquals(PlayerId(5), updated.activePlayer)
        assertNull(updated.lastEventContext)
    }
}
