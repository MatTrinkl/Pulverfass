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
    private lateinit var playerManager: PlayerManager

    @BeforeEach
    fun setUp() {
        playerManager = PlayerManager()
    }

    @Test
    fun `register sollte player speichern`() {
        val player = Player(PlayerId(1), "Max")

        playerManager.register(player)

        assertEquals(player, playerManager.get(PlayerId(1)))
    }

    @Test
    fun `register sollte exception werfen wenn playerId bereits existiert`() {
        val player = Player(PlayerId(1), "Max")

        playerManager.register(player)

        assertThrows(DuplicatePlayerIdException::class.java) {
            playerManager.register(player)
        }
    }

    @Test
    fun `get sollte null liefern wenn player nicht existiert`() {
        assertNull(playerManager.get(PlayerId(999)))
    }

    @Test
    fun `require sollte player liefern wenn er existiert`() {
        val player = Player(PlayerId(2), "Anna")
        playerManager.register(player)

        assertEquals(player, playerManager.require(PlayerId(2)))
    }

    @Test
    fun `require sollte exception werfen wenn player nicht existiert`() {
        assertThrows(PlayerNotFoundException::class.java) {
            playerManager.require(PlayerId(999))
        }
    }

    @Test
    fun `remove sollte player entfernen und zurückgeben`() {
        val player = Player(PlayerId(3), "Tom")
        playerManager.register(player)

        val removed = playerManager.remove(PlayerId(3))

        assertEquals(player, removed)
        assertFalse(playerManager.contains(PlayerId(3)))
    }

    @Test
    fun `remove sollte null liefern wenn player nicht existiert`() {
        assertNull(playerManager.remove(PlayerId(404)))
    }

    @Test
    fun `contains sollte true liefern wenn player existiert`() {
        val player = Player(PlayerId(4), "Mia")
        playerManager.register(player)

        assertTrue(playerManager.contains(PlayerId(4)))
    }

    @Test
    fun `contains sollte false liefern wenn player nicht existiert`() {
        assertFalse(playerManager.contains(PlayerId(500)))
    }

    @Test
    fun `getByConnectionId sollte passenden player liefern`() {
        val player =
            Player(
                playerId = PlayerId(5),
                username = "Luca",
                connectionId = ConnectionId(10),
            )

        playerManager.register(player)

        val result = playerManager.getByConnectionId(ConnectionId(10))
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

        playerManager.register(player)

        assertNull(playerManager.getByConnectionId(ConnectionId(999)))
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

        playerManager.register(first)
        playerManager.register(second)

        val result = playerManager.getByConnectionId(ConnectionId(20))

        assertEquals(second, result)
    }

    @Test
    fun `getByEntityId sollte passenden player liefern`() {
        val player =
            Player(
                playerId = PlayerId(9),
                username = "Nina",
                entityId = EntityId(30),
            )

        playerManager.register(player)

        assertEquals(player, playerManager.getByEntityId(EntityId(30)))
    }

    @Test
    fun `getByEntityId sollte null liefern wenn kein player passt`() {
        val player =
            Player(
                playerId = PlayerId(10),
                username = "Tom",
                entityId = EntityId(40),
            )

        playerManager.register(player)

        assertNull(playerManager.getByEntityId(EntityId(999)))
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

        playerManager.register(first)
        playerManager.register(second)

        val result = playerManager.getByEntityId(EntityId(60))

        assertEquals(second, result)
    }

    @Test
    fun `allPlayerIds sollte alle registrierten ids liefern`() {
        val first = Player(PlayerId(1), "A")
        val second = Player(PlayerId(2), "B")

        playerManager.register(first)
        playerManager.register(second)

        val result = playerManager.allPlayerIds()

        assertEquals(2, result.size)
        assertTrue(result.contains(PlayerId(1)))
        assertTrue(result.contains(PlayerId(2)))
    }

    @Test
    fun `all sollte alle registrierten player liefern`() {
        val first = Player(PlayerId(13), "A")
        val second = Player(PlayerId(14), "B")

        playerManager.register(first)
        playerManager.register(second)

        val result = playerManager.all()

        assertEquals(2, result.size)
        assertTrue(result.contains(first))
        assertTrue(result.contains(second))
    }

    @Test
    fun `clear sollte alle player entfernen`() {
        playerManager.register(Player(PlayerId(15), "A"))
        playerManager.register(Player(PlayerId(16), "B"))

        playerManager.clear()

        assertTrue(playerManager.all().isEmpty())
        assertFalse(playerManager.contains(PlayerId(15)))
        assertFalse(playerManager.contains(PlayerId(16)))
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

        playerManager.register(first)

        assertThrows(DuplicateConnectionIdException::class.java) {
            playerManager.register(second)
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

        playerManager.register(first)

        assertThrows(DuplicatePlayerEntityIdException::class.java) {
            playerManager.register(second)
        }
    }
}
