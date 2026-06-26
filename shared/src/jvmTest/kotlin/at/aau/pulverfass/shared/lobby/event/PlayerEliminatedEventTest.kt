package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PlayerEliminatedEventTest {
    @Test
    fun `valid player eliminated event is accepted`() {
        val event =
            PlayerEliminatedEvent(
                lobbyCode = LobbyCode("1278"),
                playerId = PlayerId(1),
                eliminatedByPlayerId = PlayerId(2),
            )

        assertEquals(null, event.stateVersion)
    }

    @Test
    fun `player eliminated event rejects invalid values`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlayerEliminatedEvent(
                lobbyCode = LobbyCode("1278"),
                playerId = PlayerId(1),
                eliminatedByPlayerId = PlayerId(1),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            PlayerEliminatedEvent(
                lobbyCode = LobbyCode("1278"),
                playerId = PlayerId(1),
                eliminatedByPlayerId = PlayerId(2),
                stateVersion = -1,
            )
        }
    }
}
