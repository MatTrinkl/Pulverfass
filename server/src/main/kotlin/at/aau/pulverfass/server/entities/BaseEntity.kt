package at.aau.pulverfass.server.entities

import at.aau.pulverfass.shared.ids.EntityId

/**
 * Gemeinsame Basis für alle Entities im Spiel.
 *
 * Jede Entity hat eine eindeutige ID und einen festen Typ.
 *
 * @property entityId technische ID der Entity im System
 * @property entityType Typ der Entity
 */
abstract class BaseEntity(
    open val entityId: EntityId,
    open val entityType: EntityType,
)
