package at.aau.pulverfass.server.ids

import at.aau.pulverfass.shared.ids.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests für die EntityRegistry.
 *
 * Überprüft das Registrieren, Abrufen, Entfernen und Nachschlagen von Entities.
 */
class EntityRegistryTest {
    /**
     * Einfache Test-Entity für die Registry-Tests.
     */
    private data class TestEntity(
        override val id: EntityId,
    ) : GameEntity

    @BeforeEach
    fun setUp() {
        EntityRegistry.clear()
    }

    @Test
    fun `entity sollte registriert und gefunden werden`() {
        val entity = TestEntity(EntityId(1))

        EntityRegistry.register(entity)

        val result = EntityRegistry.get(EntityId(1))

        assertEquals(entity, result)
    }

    @Test
    fun `contains sollte true liefern wenn entity existiert`() {
        val entity = TestEntity(EntityId(2))

        EntityRegistry.register(entity)

        val result = EntityRegistry.contains(EntityId(2))

        assertTrue(result)
    }

    @Test
    fun `contains sollte false liefern wenn entity nicht existiert`() {
        val result = EntityRegistry.contains(EntityId(999))

        assertFalse(result)
    }

    @Test
    fun `remove sollte entity entfernen und zurueckgeben`() {
        val entity = TestEntity(EntityId(3))

        EntityRegistry.register(entity)

        val removed = EntityRegistry.remove(EntityId(3))

        assertEquals(entity, removed)
        assertFalse(EntityRegistry.contains(EntityId(3)))
    }

    @Test
    fun `remove sollte null liefern wenn entity nicht existiert`() {
        val removed = EntityRegistry.remove(EntityId(404))

        assertEquals(null, removed)
    }

    @Test
    fun `get sollte exception werfen wenn entity nicht existiert`() {
        assertThrows(EntityNotFoundException::class.java) {
            EntityRegistry.get(EntityId(123))
        }
    }
}
