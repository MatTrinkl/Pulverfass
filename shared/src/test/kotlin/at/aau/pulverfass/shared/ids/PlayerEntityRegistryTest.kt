package at.aau.pulverfass.shared.ids

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerEntityRegistryTest {
    @Test
    fun `player sollte zugewiesene entities erhalten`() {
        val playerId = IdFactory.nextPlayerId()
        val entityId = IdFactory.nextEntityId()

        PlayerEntityRegistry.addEntity(playerId, entityId)

        val entities = PlayerEntityRegistry.getEntities(playerId)

        assertTrue(entities.contains(entityId))
    }

    @Test
    fun `entity sollte wieder dem richtigen player zugeordnet werden`() {
        val playerId = IdFactory.nextPlayerId()
        val entityId = IdFactory.nextEntityId()

        PlayerEntityRegistry.addEntity(playerId, entityId)

        val foundPlayer = PlayerEntityRegistry.getPlayer(entityId)

        assertEquals(playerId, foundPlayer)
    }

    @Test
    fun `getEntities sollte leere liste zurueckgeben wenn player keine entities hat`() {
        val playerId = IdFactory.nextPlayerId()

        val entities = PlayerEntityRegistry.getEntities(playerId)

        assertTrue(entities.isEmpty())
    }

    @Test
    fun `getPlayer sollte null zurueckgeben wenn entity keinem player zugeordnet ist`() {
        val entityId = IdFactory.nextEntityId()

        val foundPlayer = PlayerEntityRegistry.getPlayer(entityId)

        assertNull(foundPlayer)
    }
}
