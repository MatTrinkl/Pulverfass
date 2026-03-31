package at.aau.pulverfass.server.manager

import at.aau.pulverfass.server.entities.BaseEntity
import at.aau.pulverfass.server.entities.EntityType
import at.aau.pulverfass.server.ids.DuplicateEntityIdException
import at.aau.pulverfass.server.ids.EntityNotFoundException
import at.aau.pulverfass.shared.ids.EntityId

/**
 * Zentrale Verwaltung fuer alle registrierten Entities.
 *
 * Der EntityManager ist fuer folgende Aufgaben zustaendig:
 * - neue Entities registrieren
 * - Entities per ID finden
 * - Entities entfernen
 * - Entities nach Typ filtern
 *
 * Die erste Version basiert bewusst nur auf einer einfachen Map.
 */
object EntityManager {
    private val entities: MutableMap<EntityId, BaseEntity> = mutableMapOf()

    /**
     * Registriert eine neue Entity.
     *
     * Doppelte EntityIds sind nicht erlaubt.
     */
    fun register(entity: BaseEntity) {
        if (contains(entity.entityId)) {
            throw DuplicateEntityIdException(entity.entityId)
        }

        entities[entity.entityId] = entity
    }

    /**
     * Gibt eine Entity anhand ihrer ID zurueck.
     *
     * Falls keine Entity gefunden wird, wird null zurueckgegeben.
     */
    fun get(entityId: EntityId): BaseEntity? = entities[entityId]

    /**
     * Gibt eine Entity anhand ihrer ID zurueck.
     *
     * Falls keine Entity gefunden wird, wird eine Exception geworfen.
     */
    fun require(entityId: EntityId): BaseEntity =
        entities[entityId] ?: throw EntityNotFoundException(entityId)

    /**
     * Entfernt eine Entity anhand ihrer ID.
     *
     * Falls keine Entity existiert, wird null zurueckgegeben.
     */
    fun remove(entityId: EntityId): BaseEntity? = entities.remove(entityId)

    /**
     * Prueft, ob eine Entity mit dieser ID existiert.
     */
    fun contains(entityId: EntityId): Boolean = entities.containsKey(entityId)

    /**
     * Gibt alle Entities eines bestimmten Typs zurueck.
     */
    fun getByType(entityType: EntityType): List<BaseEntity> =
        entities.values.filter { it.entityType == entityType }

    /**
     * Gibt alle registrierten Entities zurueck.
     */
    fun all(): List<BaseEntity> = entities.values.toList()

    /**
     * Leert den kompletten Manager.
     *
     * Vor allem fuer Tests hilfreich.
     */
    fun clear() {
        entities.clear()
    }
}
