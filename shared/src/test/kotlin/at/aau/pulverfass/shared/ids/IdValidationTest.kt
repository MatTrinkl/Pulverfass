package at.aau.pulverfass.shared.ids

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IdValidationTest {
    @Test
    fun `playerId darf nicht 0 sein`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlayerId(0)
        }
    }

    @Test
    fun `entityId darf nicht negativ sein`() {
        assertThrows(IllegalArgumentException::class.java) {
            EntityId(-1)
        }
    }

    @Test
    fun `connectionId darf nicht 0 sein`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionId(0)
        }
    }

    @Test
    fun `sessionToken muss uuid format haben`() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionToken("not-a-uuid")
        }
    }

    @Test
    fun `gameId darf nicht negativ sein`() {
        assertThrows(IllegalArgumentException::class.java) {
            GameId(-5)
        }
    }

    @Test
    fun `lobbyCode muss genau 4 zeichen haben`() {
        assertThrows(IllegalArgumentException::class.java) {
            LobbyCode("ABC")
        }
    }

    @Test
    fun `lobbyCode darf keine kleinbuchstaben enthalten`() {
        assertThrows(IllegalArgumentException::class.java) {
            LobbyCode("ab12")
        }
    }
}
