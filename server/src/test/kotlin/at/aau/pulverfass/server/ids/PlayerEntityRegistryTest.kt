package at.aau.pulverfass.server.ids

import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests für die PlayerEntityRegistry.
 *
 * Überprüft die korrekte Zuordnung von Entities zu Playern
 * sowie das Verhalten bei Fehlerfällen.
 */
class PlayerEntityRegistryTest {
    @BeforeEach
    fun setUp() {
        PlayerEntityRegistry.clear()
    }

    @Test
    fun `entity sollte korrekt einem player zugeordnet werden`() {
        val playerId = PlayerId(1)
        val entityId = EntityId(10)

        PlayerEntityRegistry.addEntity(playerId, entityId)

        val result = PlayerEntityRegistry.getPlayer(entityId)

        assertEquals(playerId, result)
    }

    @Test
    fun `player sollte seine entities erhalten`() {
        val playerId = PlayerId(1)
        val entityId = EntityId(10)

        PlayerEntityRegistry.addEntity(playerId, entityId)

        val entities = PlayerEntityRegistry.getEntities(playerId)

        assertTrue(entities.contains(entityId))
    }

    @Test
    fun `getEntities sollte leeres set liefern wenn keine entities vorhanden sind`() {
        val playerId = PlayerId(999)

        val entities = PlayerEntityRegistry.getEntities(playerId)

        assertTrue(entities.isEmpty())
    }

    @Test
    fun `getPlayer sollte null liefern wenn entity nicht existiert`() {
        val entityId = EntityId(999)

        val result = PlayerEntityRegistry.getPlayer(entityId)

        assertNull(result)
    }

    @Test
    fun `entity darf nicht zwei verschiedenen playern zugeordnet werden`() {
        val player1 = PlayerId(1)
        val player2 = PlayerId(2)
        val entityId = EntityId(10)

        PlayerEntityRegistry.addEntity(player1, entityId)

        assertThrows(EntityAlreadyAssignedException::class.java) {
            PlayerEntityRegistry.addEntity(player2, entityId)
        }
    }

    @Test
    fun `entity darf nicht doppelt beim selben player gespeichert werden`() {
        val playerId = PlayerId(1)
        val entityId = EntityId(10)

        PlayerEntityRegistry.addEntity(playerId, entityId)
        PlayerEntityRegistry.addEntity(playerId, entityId)

        val entities = PlayerEntityRegistry.getEntities(playerId)

        assertEquals(1, entities.size)
    }

    @Test
    fun `entity sollte entfernt werden`() {
        val playerId = PlayerId(1)
        val entityId = EntityId(10)

        PlayerEntityRegistry.addEntity(playerId, entityId)
        PlayerEntityRegistry.removeEntity(playerId, entityId)

        assertTrue(PlayerEntityRegistry.getEntities(playerId).isEmpty())
    }

    @Test
    fun `removeEntity sollte nichts tun wenn player nicht existiert`() {
        val playerId = PlayerId(123)
        val entityId = EntityId(999)

        PlayerEntityRegistry.removeEntity(playerId, entityId)

        assertTrue(PlayerEntityRegistry.getEntities(playerId).isEmpty())
    }

    @Test
    fun `player sollte bestehen bleiben wenn nach remove noch weitere entities vorhanden sind`() {
        val playerId = PlayerId(1)
        val firstEntity = EntityId(10)
        val secondEntity = EntityId(11)

        PlayerEntityRegistry.addEntity(playerId, firstEntity)
        PlayerEntityRegistry.addEntity(playerId, secondEntity)

        PlayerEntityRegistry.removeEntity(playerId, firstEntity)

        val entities = PlayerEntityRegistry.getEntities(playerId)

        assertEquals(1, entities.size)
        assertTrue(entities.contains(secondEntity))
    }
}
