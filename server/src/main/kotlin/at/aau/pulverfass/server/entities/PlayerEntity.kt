package at.aau.pulverfass.server.entities

import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Erste konkrete Entity fuer einen Spieler.
 *
 * Diese Klasse verbindet das neue Entity-System
 * mit der bereits vorhandenen PlayerId.
 */
data class PlayerEntity(
    override val entityId: EntityId,
    val playerId: PlayerId,
) : BaseEntity(entityId, EntityType.PLAYER)
