package at.aau.pulverfass.server.entities

import at.aau.pulverfass.shared.ids.EntityId

/**
 * Gemeinsame Basis fuer alle Entities im Spiel.
 *
 * Jede Entity besitzt eine eindeutige EntityId
 * und einen festen EntityType.
 */
abstract class BaseEntity(
    open val entityId: EntityId,
    open val entityType: EntityType,
)
