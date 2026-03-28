package at.aau.pulverfass.shared.ids

// Speichert die Zuordnung zwischen Player und seinen Entities.
object PlayerEntityRegistry {
    private val playerToEntities: MutableMap<PlayerId, MutableList<EntityId>> = mutableMapOf()

    // Fügt eine Entity zu einem Player hinzu
    fun addEntity(
        playerId: PlayerId,
        entityId: EntityId,
    ) {
        val entities = playerToEntities.getOrPut(playerId) { mutableListOf() }
        entities.add(entityId)
    }

    // Gibt alle Entities eines Players zurück
    fun getEntities(playerId: PlayerId): List<EntityId> {
        return playerToEntities[playerId] ?: emptyList()
    }

    // Findet den Player zu einer Entity
    fun getPlayer(entityId: EntityId): PlayerId? {
        return playerToEntities.entries.find { it.value.contains(entityId) }?.key
    }
}
