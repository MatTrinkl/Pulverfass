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
    fun `gameId darf nicht negativ sein`() {
        assertThrows(IllegalArgumentException::class.java) {
            GameId(-5)
        }
    }
}
