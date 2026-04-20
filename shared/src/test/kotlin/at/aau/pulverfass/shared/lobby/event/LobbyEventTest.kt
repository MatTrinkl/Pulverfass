package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LobbyEventTest {
    @Test
    fun `should instantiate sample lobby events consistently`() {
        val lobbyCode = LobbyCode("AB12")
        val playerId = PlayerId(7)

        val events =
            listOf<LobbyEvent>(
                PlayerJoined(lobbyCode, playerId, "Alice"),
                PlayerLeft(lobbyCode, playerId, "quit"),
                TurnEnded(lobbyCode, playerId),
                LobbyCreated(lobbyCode),
                LobbyClosed(lobbyCode, "finished"),
                SystemTick(lobbyCode, tick = 5),
                TimeoutTriggered(lobbyCode, target = "turn", timeoutMillis = 30_000),
                InvalidActionDetected(lobbyCode, playerId, "move rejected"),
            )

        assertEquals(8, events.size)
        assertEquals(lobbyCode, events.first().lobbyCode)
        assertEquals("finished", (events[4] as LobbyClosed).reason)
    }

    @Test
    fun `should support exhaustive when over event hierarchies`() {
        val lobbyCode = LobbyCode("CD34")

        val rootResult =
            when (val event: LobbyEvent = LobbyCreated(lobbyCode)) {
                is ExternalLobbyEvent -> "external:${event.lobbyCode.value}"
                is InternalLobbyEvent -> "internal:${event.lobbyCode.value}"
            }

        val externalResult =
            when (val event: ExternalLobbyEvent = TurnEnded(lobbyCode, PlayerId(3))) {
                is GameStarted -> "gameStarted:${event.lobbyCode.value}"
                is PlayerJoined -> "joined:${event.playerId.value}"
                is PlayerKicked -> "kicked:${event.targetPlayerId.value}"
                is PlayerLeft -> "left:${event.playerId.value}"
                is TurnEnded -> "turnEnded:${event.playerId.value}"
            }

        val internalResult =
            when (val event: InternalLobbyEvent = LobbyClosed(lobbyCode, "done")) {
                is InvalidActionDetected -> event.reason
                is LobbyClosed -> event.reason.orEmpty()
                is LobbyCreated -> "created"
                is SystemTick -> event.tick.toString()
                is TimeoutTriggered -> event.target
            }

        assertEquals("internal:CD34", rootResult)
        assertEquals("turnEnded:3", externalResult)
        assertEquals("done", internalResult)
    }

    @Test
    fun `should forward lobby code through relevant events`() {
        val lobbyCode = LobbyCode("ZX90")
        val playerId = PlayerId(4)

        val events =
            listOf<LobbyEvent>(
                PlayerJoined(lobbyCode, playerId, "Bob"),
                PlayerLeft(lobbyCode, playerId),
                TurnEnded(lobbyCode, playerId),
                LobbyCreated(lobbyCode),
                LobbyClosed(lobbyCode),
                SystemTick(lobbyCode, 0),
                TimeoutTriggered(lobbyCode, "heartbeat", 1_000),
                InvalidActionDetected(lobbyCode, reason = "invalid"),
            )

        events.forEach { event -> assertEquals(lobbyCode, event.lobbyCode) }
    }

    @Test
    fun `should expose player left reason consistently`() {
        val leftWithReason = PlayerLeft(LobbyCode("PL12"), PlayerId(9), "quit")
        val leftWithoutReason = PlayerLeft(LobbyCode("PL12"), PlayerId(9))

        assertEquals("quit", leftWithReason.reason)
        assertEquals(null, leftWithoutReason.reason)
    }

    @Test
    fun `should expose technical event properties consistently`() {
        val invalidAction = InvalidActionDetected(LobbyCode("AA11"), PlayerId(2), "invalid")
        val tick = SystemTick(LobbyCode("BB22"), 4)
        val timeout = TimeoutTriggered(LobbyCode("CC33"), "turn", 3_000)

        assertEquals(PlayerId(2), invalidAction.playerId)
        assertEquals("invalid", invalidAction.reason)
        assertEquals(4, tick.tick)
        assertEquals("turn", timeout.target)
        assertEquals(3_000, timeout.timeoutMillis)
    }

    @Test
    fun `should validate technical event arguments`() {
        assertThrows(IllegalArgumentException::class.java) {
            InvalidActionDetected(LobbyCode("DD44"), reason = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SystemTick(LobbyCode("EE55"), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeoutTriggered(LobbyCode("FF66"), "", 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeoutTriggered(LobbyCode("GG77"), "turn", 0)
        }
    }
}
