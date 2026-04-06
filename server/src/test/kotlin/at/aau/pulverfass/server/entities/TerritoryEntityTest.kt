package at.aau.pulverfass.server.entities

import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Tests für die TerritoryEntity.
 */
class TerritoryEntityTest {
    @Test
    fun `territory entity sollte den typ territory besitzen`() {
        val territory =
            TerritoryEntity(
                entityId = EntityId(1),
                ownerId = PlayerId(10),
                troopCount = 3,
            )

        assertEquals(EntityType.TERRITORY, territory.entityType)
    }

    @Test
    fun `troops sollte den aktuellen truppenstand zurückgeben`() {
        val territory =
            TerritoryEntity(
                entityId = EntityId(2),
                ownerId = PlayerId(20),
                troopCount = 5,
            )

        assertEquals(5, territory.troops)
    }

    @Test
    fun `addTroops sollte truppen korrekt erhöhen`() {
        val territory =
            TerritoryEntity(
                entityId = EntityId(3),
                ownerId = PlayerId(30),
                troopCount = 4,
            )

        territory.addTroops(2)

        assertEquals(6, territory.troops)
    }

    @Test
    fun `subtractTroops sollte truppen korrekt verringern`() {
        val territory =
            TerritoryEntity(
                entityId = EntityId(4),
                ownerId = PlayerId(40),
                troopCount = 6,
            )

        territory.subtractTroops(2)

        assertEquals(4, territory.troops)
    }

    @Test
    fun `addTroops sollte exception werfen wenn null oder negativ hinzugefügt wird`() {
        val territory =
            TerritoryEntity(
                entityId = EntityId(5),
                ownerId = PlayerId(50),
                troopCount = 3,
            )

        assertThrows(IllegalArgumentException::class.java) {
            territory.addTroops(0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            territory.addTroops(-2)
        }
    }

    @Test
    fun `subtractTroops sollte exception werfen wenn null oder negativ entfernt wird`() {
        val territory =
            TerritoryEntity(
                entityId = EntityId(6),
                ownerId = PlayerId(60),
                troopCount = 3,
            )

        assertThrows(IllegalArgumentException::class.java) {
            territory.subtractTroops(0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            territory.subtractTroops(-1)
        }
    }

    @Test
    fun `subtractTroops sollte exception werfen wenn mehr truppen entfernt werden als vorhanden sind`() {
        val territory =
            TerritoryEntity(
                entityId = EntityId(7),
                ownerId = PlayerId(70),
                troopCount = 2,
            )

        assertThrows(IllegalArgumentException::class.java) {
            territory.subtractTroops(3)
        }
    }

    @Test
    fun `addTroops sollte validation optional überspringen können`() {
        val territory =
            TerritoryEntity(
                entityId = EntityId(8),
                ownerId = PlayerId(80),
                troopCount = 5,
            )

        territory.addTroops(-2, validateInput = false)

        assertEquals(3, territory.troops)
    }

    @Test
    fun `subtractTroops sollte validation optional überspringen können`() {
        val territory =
            TerritoryEntity(
                entityId = EntityId(9),
                ownerId = PlayerId(90),
                troopCount = 5,
            )

        territory.subtractTroops(7, validateInput = false)

        assertEquals(-2, territory.troops)
    }

    @Test
    fun `ownerId darf null sein`() {
        val territory =
            TerritoryEntity(
                entityId = EntityId(10),
                ownerId = null,
                troopCount = 1,
            )

        assertEquals(null, territory.ownerId)
    }
}
