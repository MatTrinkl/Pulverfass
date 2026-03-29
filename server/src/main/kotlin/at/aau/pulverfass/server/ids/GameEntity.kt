package at.aau.pulverfass.server.ids

import at.aau.pulverfass.shared.ids.EntityId

/**
 * Basis-Interface für serverseitige Entities.
 */
interface GameEntity {
    val id: EntityId
}
