package at.aau.pulverfass.server.player

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests fuer das fachliche Player-Modell.
 */
class PlayerTest {
    @Test
    fun `player sollte mit playerId erstellt werden koennen`() {
        val player = Player(playerId = PlayerId(1))

        assertEquals(PlayerId(1), player.playerId)
    }

    @Test
    fun `username sollte optional sein`() {
        val player = Player(playerId = PlayerId(1))

        assertNull(player.username)
    }

    @Test
    fun `connectionId sollte optional sein`() {
        val player = Player(playerId = PlayerId(1))

        assertNull(player.connectionId)
    }

    @Test
    fun `entityId sollte optional sein`() {
        val player = Player(playerId = PlayerId(1))

        assertNull(player.entityId)
    }

    @Test
    fun `player sollte alle gesetzten werte korrekt speichern`() {
        val player =
            Player(
                playerId = PlayerId(1),
                username = "Max",
                connectionId = ConnectionId(10),
                entityId = EntityId(20),
            )

        assertEquals(PlayerId(1), player.playerId)
        assertEquals("Max", player.username)
        assertEquals(ConnectionId(10), player.connectionId)
        assertEquals(EntityId(20), player.entityId)
    }
}
