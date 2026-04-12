package at.aau.pulverfass.server.manager

import at.aau.pulverfass.server.entities.BaseEntity
import at.aau.pulverfass.server.entities.EntityType
import at.aau.pulverfass.server.entities.PlayerEntity
import at.aau.pulverfass.server.ids.DuplicateEntityIdException
import at.aau.pulverfass.server.ids.DuplicatePlayerIdException
import at.aau.pulverfass.server.ids.EntityNotFoundException
import at.aau.pulverfass.server.ids.PlayerEntityBindingNotFoundException
import at.aau.pulverfass.server.ids.PlayerEntityTypeMismatchException
import at.aau.pulverfass.server.ids.PlayerNotFoundException
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
 * - konsistente Spielererstellung
 * - Lookup in beide Richtungen
 * - Rollback bei Fehlern
 * - Remove über PlayerId und EntityId
 * - null-Fälle
 * - Exception-Fälle
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
    fun `lookup sollte in beide Richtungen funktionieren`() {
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
    fun `createPlayer sollte bei doppelter playerId keinen inkonsistenten Zustand hinterlassen`() {
        val existingPlayerId = PlayerId(100)

        PlayerManager.register(
            Player(
                playerId = existingPlayerId,
                username = "BereitsDa",
            ),
        )

        val beforePlayers = PlayerManager.all().toSet()
        val beforeEntities = EntityManager.all().toSet()

        assertThrows(DuplicatePlayerIdException::class.java) {
            PlayerEntityManager.createPlayer(
                playerId = existingPlayerId,
                entityId = EntityId(201),
                username = "Anna",
            )
        }

        assertEquals(beforePlayers, PlayerManager.all().toSet())
        assertEquals(beforeEntities, EntityManager.all().toSet())
        assertFalse(EntityManager.contains(EntityId(201)))
    }

    @Test
    fun `createPlayer sollte bei doppelter entityId rollback auf player machen`() {
        val existingEntityId = EntityId(200)

        EntityManager.register(
            PlayerEntity(
                entityId = existingEntityId,
                playerId = PlayerId(9999),
            ),
        )

        val beforePlayers = PlayerManager.all().toSet()
        val beforeEntities = EntityManager.all().toSet()

        assertThrows(DuplicateEntityIdException::class.java) {
            PlayerEntityManager.createPlayer(
                playerId = PlayerId(101),
                entityId = existingEntityId,
                username = "Tom",
            )
        }

        assertEquals(beforePlayers, PlayerManager.all().toSet())
        assertEquals(beforeEntities, EntityManager.all().toSet())
        assertFalse(PlayerManager.contains(PlayerId(101)))
    }

    @Test
    fun `getEntityByPlayerId sollte null liefern wenn player nicht existiert`() {
        val result = PlayerEntityManager.getEntityByPlayerId(PlayerId(999))

        assertNull(result)
    }

    @Test
    fun `getEntityByPlayerId sollte null liefern wenn player keine entityId hat`() {
        val playerId = PlayerId(10)

        PlayerManager.register(
            Player(
                playerId = playerId,
                username = "Anna",
                entityId = null,
            ),
        )

        val result = PlayerEntityManager.getEntityByPlayerId(playerId)

        assertNull(result)
    }

    @Test
    fun `getEntityByPlayerId sollte null liefern wenn entity nicht existiert`() {
        val playerId = PlayerId(11)
        val entityId = EntityId(12)

        PlayerManager.register(
            Player(
                playerId = playerId,
                username = "Anna",
                entityId = entityId,
            ),
        )

        val result = PlayerEntityManager.getEntityByPlayerId(playerId)

        assertNull(result)
    }

    @Test
    fun `getEntityByPlayerId sollte null liefern wenn entity keine PlayerEntity ist`() {
        val playerId = PlayerId(21)
        val entityId = EntityId(22)

        PlayerManager.register(
            Player(
                playerId = playerId,
                username = "Anna",
                entityId = entityId,
            ),
        )

        EntityManager.register(
            TestEntity(entityId),
        )

        val result = PlayerEntityManager.getEntityByPlayerId(playerId)

        assertNull(result)
    }

    @Test
    fun `getPlayerByEntityId sollte null liefern wenn entity nicht existiert`() {
        val result = PlayerEntityManager.getPlayerByEntityId(EntityId(999))

        assertNull(result)
    }

    @Test
    fun `getPlayerByEntityId sollte null liefern wenn entity keine PlayerEntity ist`() {
        val entityId = EntityId(31)

        EntityManager.register(
            TestEntity(entityId),
        )

        val result = PlayerEntityManager.getPlayerByEntityId(entityId)

        assertNull(result)
    }

    @Test
    fun `getPlayerByEntityId sollte null liefern wenn player nicht existiert`() {
        val entityId = EntityId(41)

        EntityManager.register(
            PlayerEntity(
                entityId = entityId,
                playerId = PlayerId(42),
            ),
        )

        val result = PlayerEntityManager.getPlayerByEntityId(entityId)

        assertNull(result)
    }

    @Test
    fun `requireEntityByPlayerId sollte PlayerEntity zurückgeben`() {
        val player = PlayerEntityManager.createPlayer(username = "Mia")

        val entity = PlayerEntityManager.requireEntityByPlayerId(player.playerId)

        assertEquals(player.playerId, entity.playerId)
        assertEquals(player.entityId, entity.entityId)
    }

    @Test
    fun `requireEntityByPlayerId sollte Exception werfen wenn Binding fehlt`() {
        val exception =
            assertThrows(PlayerEntityBindingNotFoundException::class.java) {
                PlayerEntityManager.requireEntityByPlayerId(PlayerId(555))
            }

        assertNotNull(exception)
    }

    @Test
    fun `requirePlayerByEntityId sollte player zurückgeben`() {
        val player = PlayerEntityManager.createPlayer(username = "Noah")

        val resolvedPlayer = PlayerEntityManager.requirePlayerByEntityId(player.entityId!!)

        assertEquals(player, resolvedPlayer)
    }

    @Test
    fun `requirePlayerByEntityId sollte EntityNotFoundException werfen wenn entity fehlt`() {
        val exception =
            assertThrows(EntityNotFoundException::class.java) {
                PlayerEntityManager.requirePlayerByEntityId(EntityId(777))
            }

        assertNotNull(exception)
    }

    @Test
    fun `requirePlayerByEntityId sollte TypeMismatch werfen wenn entity keine PlayerEntity ist`() {
        val entityId = EntityId(61)

        EntityManager.register(
            TestEntity(entityId),
        )

        val exception =
            assertThrows(PlayerEntityTypeMismatchException::class.java) {
                PlayerEntityManager.requirePlayerByEntityId(entityId)
            }

        assertNotNull(exception)
    }

    @Test
    fun `requirePlayerByEntityId sollte PlayerNotFoundException werfen wenn player fehlt`() {
        val entityId = EntityId(71)
        val playerId = PlayerId(72)

        EntityManager.register(
            PlayerEntity(
                entityId = entityId,
                playerId = playerId,
            ),
        )

        val exception =
            assertThrows(PlayerNotFoundException::class.java) {
                PlayerEntityManager.requirePlayerByEntityId(entityId)
            }

        assertNotNull(exception)
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
    fun `removeByPlayerId sollte null liefern wenn player nicht existiert`() {
        val removed = PlayerEntityManager.removeByPlayerId(PlayerId(888))

        assertNull(removed)
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

    @Test
    fun `removeByEntityId sollte null liefern wenn kein Binding existiert`() {
        val removed = PlayerEntityManager.removeByEntityId(EntityId(999))

        assertNull(removed)
    }

    @Test
    fun `removeByPlayerId sollte player auch ohne entityId entfernen`() {
        val playerId = PlayerId(901)

        val player =
            Player(
                playerId = playerId,
                username = "OhneEntity",
                entityId = null,
            )

        PlayerManager.register(player)

        val removed = PlayerEntityManager.removeByPlayerId(playerId)

        assertEquals(player, removed)
        assertFalse(PlayerManager.contains(playerId))
    }

    private class TestEntity(
        override val entityId: EntityId,
    ) : BaseEntity(
            entityId = entityId,
            entityType = EntityType.TERRITORY,
        )
}
