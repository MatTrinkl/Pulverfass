package at.aau.pulverfass.server.entities

import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Repräsentiert einen Spieler im Entity-System.
 *
 * @property entityId technische ID der Entity
 * @property playerId fachliche ID des Spielers
 */
data class PlayerEntity(
    override val entityId: EntityId,
    val playerId: PlayerId,
) : BaseEntity(entityId, EntityType.PLAYER)
