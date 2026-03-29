package at.aau.pulverfass.server.ids

import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Verwaltet die Zuordnung zwischen Spielern und ihren Entities.
 */
object PlayerEntityRegistry {
    private val playerToEntities: MutableMap<PlayerId, MutableSet<EntityId>> = mutableMapOf()

    /**
     * Fügt einem Player eine Entity hinzu.
     *
     * Eine Entity darf immer nur genau einem Player zugeordnet sein.
     * Falls die Entity bereits einem anderen Player gehört, wird eine Exception geworfen.
     */
    fun addEntity(
        playerId: PlayerId,
        entityId: EntityId,
    ) {
        val currentOwner = getPlayer(entityId)

        if (currentOwner != null && currentOwner != playerId) {
            throw EntityAlreadyAssignedException(entityId, currentOwner, playerId)
        }

        val entities = playerToEntities.getOrPut(playerId) { mutableSetOf() }
        entities.add(entityId)
    }

    /**
     * Gibt alle Entities eines Players zurück.
     */
    fun getEntities(playerId: PlayerId): Set<EntityId> =
        playerToEntities[playerId]?.toSet() ?: emptySet()

    /**
     * Gibt den Player zu einer Entity zurück.
     * Falls die Entity keinem Player zugeordnet ist, wird null zurückgegeben.
     */
    fun getPlayer(entityId: EntityId): PlayerId? =
        playerToEntities.entries.firstOrNull { entityId in it.value }?.key

    /**
     * Entfernt eine Entity aus der Zuordnung eines Players.
     */
    fun removeEntity(
        playerId: PlayerId,
        entityId: EntityId,
    ) {
        val entities = playerToEntities[playerId] ?: return
        entities.remove(entityId)

        if (entities.isEmpty()) {
            playerToEntities.remove(playerId)
        }
    }
}
