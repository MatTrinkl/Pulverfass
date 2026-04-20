package at.aau.pulverfass.server.manager

import at.aau.pulverfass.server.entities.BaseEntity
import at.aau.pulverfass.server.entities.EntityType
import at.aau.pulverfass.server.ids.DuplicateEntityIdException
import at.aau.pulverfass.server.ids.EntityNotFoundException
import at.aau.pulverfass.shared.ids.EntityId

/**
 * Zentrale Verwaltung für alle registrierten Entities.
 *
 * Der EntityManager ist für folgende Aufgaben zuständig:
 * - neue Entities registrieren
 * - Entities per ID finden
 * - Entities entfernen
 * - Entities nach Typ filtern
 *
 */
internal class EntityManager {
    private val entities: MutableMap<EntityId, BaseEntity> = mutableMapOf()

    /**
     * Registriert eine neue Entity.
     *
     * Doppelte EntityIds sind nicht erlaubt.
     */
    fun register(entity: BaseEntity) {
        if (entities.putIfAbsent(entity.entityId, entity) != null) {
            throw DuplicateEntityIdException(entity.entityId)
        }
    }

    /**
     * Gibt eine Entity anhand ihrer ID zurück.
     *
     * Falls keine Entity gefunden wird, wird null zurückgegeben.
     */
    fun get(entityId: EntityId): BaseEntity? = entities[entityId]

    /**
     * Gibt eine Entity anhand ihrer ID zurück.
     *
     * Falls keine Entity gefunden wird, wird eine Exception geworfen.
     */
    fun require(entityId: EntityId): BaseEntity =
        entities[entityId] ?: throw EntityNotFoundException(entityId)

    /**
     * Entfernt eine Entity anhand ihrer ID.
     *
     * Falls keine Entity existiert, wird null zurückgegeben.
     */
    fun remove(entityId: EntityId): BaseEntity? = entities.remove(entityId)

    /**
     * Prüft, ob eine Entity mit dieser ID existiert.
     */
    fun contains(entityId: EntityId): Boolean = entities.containsKey(entityId)

    /**
     * Gibt alle Entities eines bestimmten Typs zurück.
     */
    fun getByType(entityType: EntityType): List<BaseEntity> =
        entities.values.filter { it.entityType == entityType }

    /**
     * Gibt alle registrierten Entity-IDs zurück.
     */
    fun allEntityIds(): Set<EntityId> = entities.keys.toSet()

    /**
     * Gibt alle registrierten Entities zurück.
     */
    fun all(): List<BaseEntity> = entities.values.toList()

    /**
     * Leert den kompletten Manager.
     *
     * Vor allem für Tests hilfreich.
     */
    fun clear() {
        entities.clear()
    }
}
