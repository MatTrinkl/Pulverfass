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
    private lateinit var playerManager: PlayerManager
    private lateinit var entityManager: EntityManager
    private lateinit var playerEntityManager: PlayerEntityManager

    @BeforeEach
    fun setUp() {
        playerManager = PlayerManager()
        entityManager = EntityManager()
        playerEntityManager = PlayerEntityManager.createForTest(playerManager, entityManager)
    }

    @Test
    fun `createPlayer sollte player und playerEntity konsistent anlegen`() {
        val player =
            playerEntityManager.createPlayer(
                username = "Anna",
                connectionId = ConnectionId(10),
            )
        val entityId = player.entityId!!

        assertEquals("Anna", player.username)
        assertEquals(ConnectionId(10), player.connectionId)
        assertNotNull(player.entityId)
        assertTrue(playerManager.contains(player.playerId))
        assertTrue(entityManager.contains(entityId))

        val entity = entityManager.require(entityId)
        assertTrue(entity is PlayerEntity)
        assertEquals(player.playerId, (entity as PlayerEntity).playerId)
    }

    @Test
    fun `lookup sollte in beide Richtungen funktionieren`() {
        val player = playerEntityManager.createPlayer(username = "Max")
        val entityId = player.entityId!!

        val entity = playerEntityManager.getEntityByPlayerId(player.playerId)
        val resolvedPlayer = playerEntityManager.getPlayerByEntityId(entityId)

        assertNotNull(entity)
        assertEquals(player.playerId, entity!!.playerId)
        assertEquals(entityId, entity.entityId)
        assertEquals(player, resolvedPlayer)
    }

    @Test
    fun `createPlayer sollte bei doppelter playerId keinen inkonsistenten Zustand hinterlassen`() {
        val existingPlayerId = PlayerId(100)

        playerManager.register(
            Player(
                playerId = existingPlayerId,
                username = "BereitsDa",
            ),
        )

        val beforePlayers = playerManager.all().toSet()
        val beforeEntities = entityManager.all().toSet()

        assertThrows(DuplicatePlayerIdException::class.java) {
            playerEntityManager.createPlayer(
                playerId = existingPlayerId,
                entityId = EntityId(201),
                username = "Anna",
            )
        }

        assertEquals(beforePlayers, playerManager.all().toSet())
        assertEquals(beforeEntities, entityManager.all().toSet())
        assertFalse(entityManager.contains(EntityId(201)))
    }

    @Test
    fun `createPlayer sollte bei doppelter entityId rollback auf player machen`() {
        val existingEntityId = EntityId(200)

        entityManager.register(
            PlayerEntity(
                entityId = existingEntityId,
                playerId = PlayerId(9999),
            ),
        )

        val beforePlayers = playerManager.all().toSet()
        val beforeEntities = entityManager.all().toSet()

        assertThrows(DuplicateEntityIdException::class.java) {
            playerEntityManager.createPlayer(
                playerId = PlayerId(101),
                entityId = existingEntityId,
                username = "Tom",
            )
        }

        assertEquals(beforePlayers, playerManager.all().toSet())
        assertEquals(beforeEntities, entityManager.all().toSet())
        assertFalse(playerManager.contains(PlayerId(101)))
    }

    @Test
    fun `getEntityByPlayerId sollte null liefern wenn player nicht existiert`() {
        val result = playerEntityManager.getEntityByPlayerId(PlayerId(999))

        assertNull(result)
    }

    @Test
    fun `getEntityByPlayerId sollte null liefern wenn player keine entityId hat`() {
        val playerId = PlayerId(10)

        playerManager.register(
            Player(
                playerId = playerId,
                username = "Anna",
                entityId = null,
            ),
        )

        val result = playerEntityManager.getEntityByPlayerId(playerId)

        assertNull(result)
    }

    @Test
    fun `getEntityByPlayerId sollte null liefern wenn entity nicht existiert`() {
        val playerId = PlayerId(11)
        val entityId = EntityId(12)

        playerManager.register(
            Player(
                playerId = playerId,
                username = "Anna",
                entityId = entityId,
            ),
        )

        val result = playerEntityManager.getEntityByPlayerId(playerId)

        assertNull(result)
    }

    @Test
    fun `getEntityByPlayerId sollte null liefern wenn entity keine PlayerEntity ist`() {
        val playerId = PlayerId(21)
        val entityId = EntityId(22)

        playerManager.register(
            Player(
                playerId = playerId,
                username = "Anna",
                entityId = entityId,
            ),
        )

        entityManager.register(
            TestEntity(entityId),
        )

        val result = playerEntityManager.getEntityByPlayerId(playerId)

        assertNull(result)
    }

    @Test
    fun `getPlayerByEntityId sollte null liefern wenn entity nicht existiert`() {
        val result = playerEntityManager.getPlayerByEntityId(EntityId(999))

        assertNull(result)
    }

    @Test
    fun `getPlayerByEntityId sollte null liefern wenn entity keine PlayerEntity ist`() {
        val entityId = EntityId(31)

        entityManager.register(
            TestEntity(entityId),
        )

        val result = playerEntityManager.getPlayerByEntityId(entityId)

        assertNull(result)
    }

    @Test
    fun `getPlayerByEntityId sollte null liefern wenn player nicht existiert`() {
        val entityId = EntityId(41)

        entityManager.register(
            PlayerEntity(
                entityId = entityId,
                playerId = PlayerId(42),
            ),
        )

        val result = playerEntityManager.getPlayerByEntityId(entityId)

        assertNull(result)
    }

    @Test
    fun `requireEntityByPlayerId sollte PlayerEntity zurueckgeben`() {
        val player = playerEntityManager.createPlayer(username = "Mia")

        val entity = playerEntityManager.requireEntityByPlayerId(player.playerId)

        assertEquals(player.playerId, entity.playerId)
        assertEquals(player.entityId, entity.entityId)
    }

    @Test
    fun `requireEntityByPlayerId sollte exception werfen wenn Binding fehlt`() {
        val exception =
            assertThrows(PlayerEntityBindingNotFoundException::class.java) {
                playerEntityManager.requireEntityByPlayerId(PlayerId(555))
            }

        assertNotNull(exception)
    }

    @Test
    fun `requirePlayerByEntityId sollte player zurueckgeben`() {
        val player = playerEntityManager.createPlayer(username = "Noah")
        val entityId = player.entityId!!

        val resolvedPlayer = playerEntityManager.requirePlayerByEntityId(entityId)

        assertEquals(player, resolvedPlayer)
    }

    @Test
    fun `requirePlayerByEntityId sollte EntityNotFoundException werfen wenn entity fehlt`() {
        val exception =
            assertThrows(EntityNotFoundException::class.java) {
                playerEntityManager.requirePlayerByEntityId(EntityId(777))
            }

        assertNotNull(exception)
    }

    @Test
    fun `requirePlayerByEntityId sollte TypeMismatch werfen wenn entity keine PlayerEntity ist`() {
        val entityId = EntityId(61)

        entityManager.register(
            TestEntity(entityId),
        )

        val exception =
            assertThrows(PlayerEntityTypeMismatchException::class.java) {
                playerEntityManager.requirePlayerByEntityId(entityId)
            }

        assertNotNull(exception)
    }

    @Test
    fun `requirePlayerByEntityId sollte PlayerNotFoundException werfen wenn player fehlt`() {
        val entityId = EntityId(71)
        val playerId = PlayerId(72)

        entityManager.register(
            PlayerEntity(
                entityId = entityId,
                playerId = playerId,
            ),
        )

        val exception =
            assertThrows(PlayerNotFoundException::class.java) {
                playerEntityManager.requirePlayerByEntityId(entityId)
            }

        assertNotNull(exception)
    }

    @Test
    fun `removeByPlayerId sollte player und entity konsistent entfernen`() {
        val player = playerEntityManager.createPlayer(username = "Tom")
        val entityId = player.entityId!!

        val removed = playerEntityManager.removeByPlayerId(player.playerId)

        assertEquals(player, removed)
        assertFalse(playerManager.contains(player.playerId))
        assertFalse(entityManager.contains(entityId))
        assertNull(playerManager.getByEntityId(entityId))
    }

    @Test
    fun `removeByPlayerId sollte null liefern wenn player nicht existiert`() {
        val removed = playerEntityManager.removeByPlayerId(PlayerId(888))

        assertNull(removed)
    }

    @Test
    fun `removeByEntityId sollte player und entity konsistent entfernen`() {
        val player = playerEntityManager.createPlayer(username = "Mia")
        val entityId = player.entityId!!

        val removed = playerEntityManager.removeByEntityId(entityId)

        assertEquals(player, removed)
        assertFalse(playerManager.contains(player.playerId))
        assertFalse(entityManager.contains(entityId))
        assertNull(playerManager.getByEntityId(entityId))
    }

    @Test
    fun `removeByEntityId sollte null liefern wenn kein Binding existiert`() {
        val removed = playerEntityManager.removeByEntityId(EntityId(999))

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

        playerManager.register(player)

        val removed = playerEntityManager.removeByPlayerId(playerId)

        assertEquals(player, removed)
        assertFalse(playerManager.contains(playerId))
    }

    private class TestEntity(
        override val entityId: EntityId,
    ) : BaseEntity(
            entityId = entityId,
            entityType = EntityType.TERRITORY,
        )
}
