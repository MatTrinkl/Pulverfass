package at.aau.pulverfass.shared.ids

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerEntityRegistryTest {
    @Test
    fun `player should get assigned entities`() {
        val playerId = IdFactory.nextPlayerId()
        val entityId = IdFactory.nextEntityId()

        PlayerEntityRegistry.addEntity(playerId, entityId)

        val entities = PlayerEntityRegistry.getEntities(playerId)

        assertTrue(entities.contains(entityId))
    }

    @Test
    fun `entity should map back to player`() {
        val playerId = IdFactory.nextPlayerId()
        val entityId = IdFactory.nextEntityId()

        PlayerEntityRegistry.addEntity(playerId, entityId)

        val foundPlayer = PlayerEntityRegistry.getPlayer(entityId)

        assertEquals(playerId, foundPlayer)
    }
}
