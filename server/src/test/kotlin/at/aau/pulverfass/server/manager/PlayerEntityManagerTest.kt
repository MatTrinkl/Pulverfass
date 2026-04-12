package at.aau.pulverfass.server.manager

import at.aau.pulverfass.server.entities.PlayerEntity
import at.aau.pulverfass.server.ids.DuplicateEntityIdException
import at.aau.pulverfass.server.ids.DuplicatePlayerIdException
import at.aau.pulverfass.server.player.Player
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests für den PlayerEntityManager.
 *
 * Geprüft werden:
 * - Happy Path für die konsistente Erstellung
 * - Lookup in beide Richtungen
 * - Rollback bei Fehlern
 * - Remove über PlayerId und EntityId
 */
class PlayerEntityManagerTest {
    @BeforeEach
    fun setUp() {
        PlayerManager.clear()
        EntityManager.clear()
    }

    @Test
    fun `createPlayer sollte player und playerEntity konsistent anlegen`() {
        val player =
            PlayerEntityManager.createPlayer(
                username = "Anna",
                connectionId = ConnectionId(10),
            )

        assertEquals("Anna", player.username)
        assertEquals(ConnectionId(10), player.connectionId)
        assertNotNull(player.entityId)
        assertTrue(PlayerManager.contains(player.playerId))
        assertTrue(EntityManager.contains(player.entityId!!))

        val entity = EntityManager.require(player.entityId!!)
        assertTrue(entity is PlayerEntity)
        assertEquals(player.playerId, (entity as PlayerEntity).playerId)
    }

    @Test
    fun `lookup sollte in beide richtungen funktionieren`() {
        val player = PlayerEntityManager.createPlayer(username = "Max")
        val entityId = player.entityId!!

        val entity = PlayerEntityManager.getEntityByPlayerId(player.playerId)
        val resolvedPlayer = PlayerEntityManager.getPlayerByEntityId(entityId)

        assertNotNull(entity)
        assertEquals(player.playerId, entity!!.playerId)
        assertEquals(entityId, entity.entityId)
        assertEquals(player, resolvedPlayer)
    }

    @Test
    fun `createPlayer sollte bei fehlern keinen inkonsistenten zustand hinterlassen`() {
        val existingPlayerId = PlayerId(100)
        val existingEntityId = EntityId(200)

        PlayerManager.register(
            Player(
                playerId = existingPlayerId,
                username = "BereitsDa",
            ),
        )

        EntityManager.register(
            PlayerEntity(
                entityId = existingEntityId,
                playerId = PlayerId(9999),
            ),
        )

        val beforePlayers = PlayerManager.all().toSet()
        val beforeEntities = EntityManager.all().toSet()

        val playerException =
            assertThrows(DuplicatePlayerIdException::class.java) {
                PlayerEntityManager.createPlayer(
                    playerId = existingPlayerId,
                    entityId = EntityId(201),
                    username = "Anna",
                )
            }

        assertNotNull(playerException)
        assertEquals(beforePlayers, PlayerManager.all().toSet())
        assertEquals(beforeEntities, EntityManager.all().toSet())
        assertFalse(EntityManager.contains(EntityId(201)))

        val entityException =
            assertThrows(DuplicateEntityIdException::class.java) {
                PlayerEntityManager.createPlayer(
                    playerId = PlayerId(101),
                    entityId = existingEntityId,
                    username = "Tom",
                )
            }

        assertNotNull(entityException)
        assertEquals(beforePlayers, PlayerManager.all().toSet())
        assertEquals(beforeEntities, EntityManager.all().toSet())
        assertFalse(PlayerManager.contains(PlayerId(101)))
    }

    @Test
    fun `removeByPlayerId sollte player und entity konsistent entfernen`() {
        val player = PlayerEntityManager.createPlayer(username = "Tom")
        val entityId = player.entityId!!

        val removed = PlayerEntityManager.removeByPlayerId(player.playerId)

        assertEquals(player, removed)
        assertFalse(PlayerManager.contains(player.playerId))
        assertFalse(EntityManager.contains(entityId))
        assertNull(PlayerManager.getByEntityId(entityId))
    }

    @Test
    fun `removeByEntityId sollte player und entity konsistent entfernen`() {
        val player = PlayerEntityManager.createPlayer(username = "Mia")
        val entityId = player.entityId!!

        val removed = PlayerEntityManager.removeByEntityId(entityId)

        assertEquals(player, removed)
        assertFalse(PlayerManager.contains(player.playerId))
        assertFalse(EntityManager.contains(entityId))
        assertNull(PlayerManager.getByEntityId(entityId))
    }
}
