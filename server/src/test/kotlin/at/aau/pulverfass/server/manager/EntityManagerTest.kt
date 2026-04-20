package at.aau.pulverfass.server.manager

import at.aau.pulverfass.server.entities.BaseEntity
import at.aau.pulverfass.server.entities.EntityType
import at.aau.pulverfass.server.entities.PlayerEntity
import at.aau.pulverfass.server.entities.TerritoryEntity
import at.aau.pulverfass.server.ids.DuplicateEntityIdException
import at.aau.pulverfass.server.ids.EntityNotFoundException
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
 * Tests für den EntityManager.
 *
 * Geprüft werden:
 * - Registrierung
 * - Lookup
 * - Remove
 * - Iteration
 * - Filterung nach Typ
 * - Fehlerfälle
 */
class EntityManagerTest {
    /**
     * Einfache Test-Entity für allgemeine Manager-Tests.
     */
    private data class TestEntity(
        override val entityId: EntityId,
        override val entityType: EntityType,
    ) : BaseEntity(entityId, entityType)

    private lateinit var entityManager: EntityManager

    @BeforeEach
    fun setUp() {
        entityManager = EntityManager()
    }

    @Test
    fun `register sollte entity speichern`() {
        val entity = TestEntity(EntityId(1), EntityType.PLAYER)

        entityManager.register(entity)

        assertEquals(entity, entityManager.get(EntityId(1)))
    }

    @Test
    fun `register sollte doppelte entity ids verhindern`() {
        val entity = TestEntity(EntityId(2), EntityType.PLAYER)

        entityManager.register(entity)

        assertThrows(DuplicateEntityIdException::class.java) {
            entityManager.register(entity)
        }
    }

    @Test
    fun `get sollte null liefern wenn entity nicht existiert`() {
        val result = entityManager.get(EntityId(999))

        assertNull(result)
    }

    @Test
    fun `require sollte entity liefern wenn sie existiert`() {
        val entity = TestEntity(EntityId(3), EntityType.TERRITORY)
        entityManager.register(entity)

        val result = entityManager.require(EntityId(3))

        assertEquals(entity, result)
    }

    @Test
    fun `require sollte exception werfen wenn entity nicht existiert`() {
        assertThrows(EntityNotFoundException::class.java) {
            entityManager.require(EntityId(404))
        }
    }

    @Test
    fun `remove sollte entity entfernen und zurückgeben`() {
        val entity = TestEntity(EntityId(4), EntityType.TERRITORY)
        entityManager.register(entity)

        val removed = entityManager.remove(EntityId(4))

        assertEquals(entity, removed)
        assertFalse(entityManager.contains(EntityId(4)))
    }

    @Test
    fun `remove sollte null liefern wenn entity nicht existiert`() {
        val removed = entityManager.remove(EntityId(405))

        assertNull(removed)
    }

    @Test
    fun `contains sollte true liefern wenn entity existiert`() {
        val entity = TestEntity(EntityId(5), EntityType.PLAYER)
        entityManager.register(entity)

        assertTrue(entityManager.contains(EntityId(5)))
    }

    @Test
    fun `contains sollte false liefern wenn entity nicht existiert`() {
        assertFalse(entityManager.contains(EntityId(500)))
    }

    @Test
    fun `getByType sollte nur entities des passenden typs liefern`() {
        val player =
            PlayerEntity(
                entityId = EntityId(6),
                playerId = PlayerId(1),
            )

        val territory1 =
            TerritoryEntity(
                entityId = EntityId(7),
                ownerId = PlayerId(1),
                troopCount = 3,
            )

        val territory2 =
            TerritoryEntity(
                entityId = EntityId(8),
                ownerId = null,
                troopCount = 3,
            )

        entityManager.register(player)
        entityManager.register(territory1)
        entityManager.register(territory2)

        val result = entityManager.getByType(EntityType.TERRITORY)

        assertEquals(2, result.size)
        assertTrue(result.contains(territory1))
        assertTrue(result.contains(territory2))
    }

    @Test
    fun `getByType sollte leere liste liefern wenn kein typ vorhanden ist`() {
        val player =
            PlayerEntity(
                entityId = EntityId(9),
                playerId = PlayerId(2),
            )

        entityManager.register(player)

        val result = entityManager.getByType(EntityType.TERRITORY)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `allEntityIds sollte alle registrierten ids liefern`() {
        val player =
            PlayerEntity(
                entityId = EntityId(20),
                playerId = PlayerId(1),
            )

        val territory =
            TerritoryEntity(
                entityId = EntityId(21),
                ownerId = PlayerId(1),
                troopCount = 3,
            )

        entityManager.register(player)
        entityManager.register(territory)

        val result = entityManager.allEntityIds()

        assertEquals(2, result.size)
        assertTrue(result.contains(EntityId(20)))
        assertTrue(result.contains(EntityId(21)))
    }

    @Test
    fun `all sollte alle registrierten entities liefern`() {
        val player =
            PlayerEntity(
                entityId = EntityId(10),
                playerId = PlayerId(3),
            )

        val territory =
            TerritoryEntity(
                entityId = EntityId(11),
                ownerId = PlayerId(3),
                troopCount = 3,
            )

        entityManager.register(player)
        entityManager.register(territory)

        val result = entityManager.all()

        assertEquals(2, result.size)
        assertTrue(result.contains(player))
        assertTrue(result.contains(territory))
    }

    @Test
    fun `clear sollte alle entities entfernen`() {
        val player =
            PlayerEntity(
                entityId = EntityId(12),
                playerId = PlayerId(4),
            )

        val territory =
            TerritoryEntity(
                entityId = EntityId(13),
                ownerId = PlayerId(4),
                troopCount = 3,
            )

        entityManager.register(player)
        entityManager.register(territory)

        entityManager.clear()

        assertTrue(entityManager.all().isEmpty())
        assertFalse(entityManager.contains(EntityId(12)))
        assertFalse(entityManager.contains(EntityId(13)))
    }
}
