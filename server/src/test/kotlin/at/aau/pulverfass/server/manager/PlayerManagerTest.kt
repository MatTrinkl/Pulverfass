package at.aau.pulverfass.server.manager

import at.aau.pulverfass.server.ids.DuplicateConnectionIdException
import at.aau.pulverfass.server.ids.DuplicatePlayerEntityIdException
import at.aau.pulverfass.server.ids.DuplicatePlayerIdException
import at.aau.pulverfass.server.ids.PlayerNotFoundException
import at.aau.pulverfass.server.player.Player
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests für den PlayerManager.
 *
 * Geprüft werden:
 * - Registrierung von Playern
 * - Lookup (get / require)
 * - Entfernen von Playern
 * - Abfragen (contains, all)
 * - Lookup über ConnectionId und EntityId
 * - Fehlerfälle (Exceptions)
 */
class PlayerManagerTest {
    @BeforeEach
    fun setUp() {
        // vor jedem Test wird der Manager geleert
        PlayerManager.clear()
    }

    @Test
    fun `register sollte player speichern`() {
        val player = Player(PlayerId(1), "Max")

        PlayerManager.register(player)

        assertEquals(player, PlayerManager.get(PlayerId(1)))
    }

    @Test
    fun `register sollte exception werfen wenn playerId bereits existiert`() {
        val player = Player(PlayerId(1), "Max")

        PlayerManager.register(player)

        assertThrows(DuplicatePlayerIdException::class.java) {
            PlayerManager.register(player)
        }
    }

    @Test
    fun `get sollte null liefern wenn player nicht existiert`() {
        assertNull(PlayerManager.get(PlayerId(999)))
    }

    @Test
    fun `require sollte player liefern wenn er existiert`() {
        val player = Player(PlayerId(2), "Anna")
        PlayerManager.register(player)

        assertEquals(player, PlayerManager.require(PlayerId(2)))
    }

    @Test
    fun `require sollte exception werfen wenn player nicht existiert`() {
        assertThrows(PlayerNotFoundException::class.java) {
            PlayerManager.require(PlayerId(999))
        }
    }

    @Test
    fun `remove sollte player entfernen und zurückgeben`() {
        val player = Player(PlayerId(3), "Tom")
        PlayerManager.register(player)

        val removed = PlayerManager.remove(PlayerId(3))

        assertEquals(player, removed)
        assertFalse(PlayerManager.contains(PlayerId(3)))
    }

    @Test
    fun `remove sollte null liefern wenn player nicht existiert`() {
        assertNull(PlayerManager.remove(PlayerId(404)))
    }

    @Test
    fun `contains sollte true liefern wenn player existiert`() {
        val player = Player(PlayerId(4), "Mia")
        PlayerManager.register(player)

        assertTrue(PlayerManager.contains(PlayerId(4)))
    }

    @Test
    fun `contains sollte false liefern wenn player nicht existiert`() {
        assertFalse(PlayerManager.contains(PlayerId(500)))
    }

    // ConnectionId Tests
    @Test
    fun `getByConnectionId sollte passenden player liefern`() {
        val player =
            Player(
                playerId = PlayerId(5),
                username = "Luca",
                connectionId = ConnectionId(10),
            )

        PlayerManager.register(player)

        val result = PlayerManager.getByConnectionId(ConnectionId(10))
        assertEquals(player, result)
    }

    @Test
    fun `getByConnectionId sollte null liefern wenn kein player passt`() {
        val player =
            Player(
                playerId = PlayerId(6),
                username = "Max",
                connectionId = ConnectionId(10),
            )

        PlayerManager.register(player)

        assertNull(PlayerManager.getByConnectionId(ConnectionId(999)))
    }

    @Test
    fun `getByConnectionId sollte mehrere player durchsuchen`() {
        val first =
            Player(
                playerId = PlayerId(7),
                username = "A",
                connectionId = ConnectionId(10),
            )
        val second =
            Player(
                playerId = PlayerId(8),
                username = "B",
                connectionId = ConnectionId(20),
            )

        PlayerManager.register(first)
        PlayerManager.register(second)

        val result = PlayerManager.getByConnectionId(ConnectionId(20))

        assertEquals(second, result)
    }

    // EntityId Tests
    @Test
    fun `getByEntityId sollte passenden player liefern`() {
        val player =
            Player(
                playerId = PlayerId(9),
                username = "Nina",
                entityId = EntityId(30),
            )

        PlayerManager.register(player)

        assertEquals(player, PlayerManager.getByEntityId(EntityId(30)))
    }

    @Test
    fun `getByEntityId sollte null liefern wenn kein player passt`() {
        val player =
            Player(
                playerId = PlayerId(10),
                username = "Tom",
                entityId = EntityId(40),
            )

        PlayerManager.register(player)

        assertNull(PlayerManager.getByEntityId(EntityId(999)))
    }

    @Test
    fun `getByEntityId sollte mehrere player durchsuchen`() {
        val first =
            Player(
                playerId = PlayerId(11),
                username = "A",
                entityId = EntityId(50),
            )
        val second =
            Player(
                playerId = PlayerId(12),
                username = "B",
                entityId = EntityId(60),
            )

        PlayerManager.register(first)
        PlayerManager.register(second)

        val result = PlayerManager.getByEntityId(EntityId(60))

        assertEquals(second, result)
    }

    @Test
    fun `allPlayerIds sollte alle registrierten ids liefern`() {
        val first = Player(PlayerId(1), "A")
        val second = Player(PlayerId(2), "B")

        PlayerManager.register(first)
        PlayerManager.register(second)

        val result = PlayerManager.allPlayerIds()

        assertEquals(2, result.size)
        assertTrue(result.contains(PlayerId(1)))
        assertTrue(result.contains(PlayerId(2)))
    }

    @Test
    fun `all sollte alle registrierten player liefern`() {
        val first = Player(PlayerId(13), "A")
        val second = Player(PlayerId(14), "B")

        PlayerManager.register(first)
        PlayerManager.register(second)

        val result = PlayerManager.all()

        assertEquals(2, result.size)
        assertTrue(result.contains(first))
        assertTrue(result.contains(second))
    }

    @Test
    fun `clear sollte alle player entfernen`() {
        PlayerManager.register(Player(PlayerId(15), "A"))
        PlayerManager.register(Player(PlayerId(16), "B"))

        PlayerManager.clear()

        assertTrue(PlayerManager.all().isEmpty())
        assertFalse(PlayerManager.contains(PlayerId(15)))
        assertFalse(PlayerManager.contains(PlayerId(16)))
    }

    @Test
    fun `register sollte exception werfen wenn connectionId bereits vergeben ist`() {
        val first =
            Player(
                playerId = PlayerId(1),
                username = "A",
                connectionId = ConnectionId(10),
            )
        val second =
            Player(
                playerId = PlayerId(2),
                username = "B",
                connectionId = ConnectionId(10),
            )

        PlayerManager.register(first)

        assertThrows(DuplicateConnectionIdException::class.java) {
            PlayerManager.register(second)
        }
    }

    @Test
    fun `register sollte exception werfen wenn entityId bereits vergeben ist`() {
        val first =
            Player(
                playerId = PlayerId(1),
                username = "A",
                entityId = EntityId(20),
            )
        val second =
            Player(
                playerId = PlayerId(2),
                username = "B",
                entityId = EntityId(20),
            )

        PlayerManager.register(first)

        assertThrows(DuplicatePlayerEntityIdException::class.java) {
            PlayerManager.register(second)
        }
    }
}
