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
 * Tests fuer den EntityManager.
 *
 * Geprueft werden:
 * - Registrierung
 * - Lookup
 * - Remove
 * - Iteration
 * - Filterung nach Typ
 * - Fehlerfaelle
 */
class EntityManagerTest {
    /**
     * Einfache Test-Entity fuer allgemeine Manager-Tests.
     */
    private data class TestEntity(
        override val entityId: EntityId,
        override val entityType: EntityType,
    ) : BaseEntity(entityId, entityType)

    @BeforeEach
    fun setUp() {
        EntityManager.clear()
    }

    @Test
    fun `register sollte entity speichern`() {
        val entity = TestEntity(EntityId(1), EntityType.PLAYER)

        EntityManager.register(entity)

        assertEquals(entity, EntityManager.get(EntityId(1)))
    }

    @Test
    fun `register sollte doppelte entity ids verhindern`() {
        val entity = TestEntity(EntityId(2), EntityType.PLAYER)

        EntityManager.register(entity)

        assertThrows(DuplicateEntityIdException::class.java) {
            EntityManager.register(entity)
        }
    }

    @Test
    fun `get sollte null liefern wenn entity nicht existiert`() {
        val result = EntityManager.get(EntityId(999))

        assertNull(result)
    }

    @Test
    fun `require sollte entity liefern wenn sie existiert`() {
        val entity = TestEntity(EntityId(3), EntityType.TERRITORY)
        EntityManager.register(entity)

        val result = EntityManager.require(EntityId(3))

        assertEquals(entity, result)
    }

    @Test
    fun `require sollte exception werfen wenn entity nicht existiert`() {
        assertThrows(EntityNotFoundException::class.java) {
            EntityManager.require(EntityId(404))
        }
    }

    @Test
    fun `remove sollte entity entfernen und zurueckgeben`() {
        val entity = TestEntity(EntityId(4), EntityType.TERRITORY)
        EntityManager.register(entity)

        val removed = EntityManager.remove(EntityId(4))

        assertEquals(entity, removed)
        assertFalse(EntityManager.contains(EntityId(4)))
    }

    @Test
    fun `remove sollte null liefern wenn entity nicht existiert`() {
        val removed = EntityManager.remove(EntityId(405))

        assertNull(removed)
    }

    @Test
    fun `contains sollte true liefern wenn entity existiert`() {
        val entity = TestEntity(EntityId(5), EntityType.PLAYER)
        EntityManager.register(entity)

        assertTrue(EntityManager.contains(EntityId(5)))
    }

    @Test
    fun `contains sollte false liefern wenn entity nicht existiert`() {
        assertFalse(EntityManager.contains(EntityId(500)))
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

        EntityManager.register(player)
        EntityManager.register(territory1)
        EntityManager.register(territory2)

        val result = EntityManager.getByType(EntityType.TERRITORY)

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

        EntityManager.register(player)

        val result = EntityManager.getByType(EntityType.TERRITORY)

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

        EntityManager.register(player)
        EntityManager.register(territory)

        val result = EntityManager.allEntityIds()

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

        EntityManager.register(player)
        EntityManager.register(territory)

        val result = EntityManager.all()

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

        EntityManager.register(player)
        EntityManager.register(territory)

        EntityManager.clear()

        assertTrue(EntityManager.all().isEmpty())
        assertFalse(EntityManager.contains(EntityId(12)))
        assertFalse(EntityManager.contains(EntityId(13)))
    }
}
