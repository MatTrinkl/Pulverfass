package at.aau.pulverfass.server.ids

import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.server.ids.EntityNotFoundException

/**
 * Zentrale Registry für alle serverseitigen Entities.
 *
 * Damit kann jederzeit eine Entity über ihre EntityId gefunden werden.
 */
object EntityRegistry {
    private val entities: MutableMap<EntityId, GameEntity> = mutableMapOf()

    /**
     * Registriert eine Entity in der Registry.
     */
    fun register(entity: GameEntity) {
        entities[entity.id] = entity
    }

    /**
     * Gibt die Entity zu einer EntityId zurück.
     *
     * Wirft eine Exception, wenn keine Entity mit dieser ID vorhanden ist.
     */
    fun get(entityId: EntityId): GameEntity =
        entities[entityId] ?: throw EntityNotFoundException(entityId)

    /**
     * Entfernt eine Entity aus der Registry.
     */
    fun remove(entityId: EntityId): GameEntity? = entities.remove(entityId)

    /**
     * Prüft, ob eine Entity mit der ID vorhanden ist.
     */
    fun contains(entityId: EntityId): Boolean = entities.containsKey(entityId)

    /**
     * Leert die Registry.
     *
     * Wird vor allem für Tests verwendet, damit jeder Test mit einem sauberen Zustand startet.
     */
    fun clear() {
        entities.clear()
    }
}
