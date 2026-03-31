package at.aau.pulverfass.server.entities

import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Ein Gebiet auf der Risiko-Karte.
 *
 * ownerId:
 * - der Spieler, dem das Gebiet aktuell gehoert
 *
 * troops:
 * - Anzahl der Truppen auf diesem Gebiet
 */
data class TerritoryEntity(
    override val entityId: EntityId,
    val ownerId: PlayerId?,
    val troops: Int,
) : BaseEntity(entityId, EntityType.TERRITORY)
