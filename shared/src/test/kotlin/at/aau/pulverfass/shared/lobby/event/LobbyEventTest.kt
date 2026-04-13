package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LobbyEventTest {
    @Test
    fun `should instantiate sample lobby events consistently`() {
        val lobbyCode = LobbyCode("AB12")
        val playerId = PlayerId(7)

        val events =
            listOf<LobbyEvent>(
                PlayerJoined(lobbyCode, playerId),
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
                is PlayerJoined -> "joined:${event.playerId.value}"
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
                PlayerJoined(lobbyCode, playerId),
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
}
