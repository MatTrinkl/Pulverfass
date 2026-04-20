package at.aau.pulverfass.shared.map.config

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.TerritoryId

/**
 * Normalisierte und validierte Laufzeitrepräsentation einer Map.
 */
data class MapDefinition(
    val schemaVersion: Int,
    val territories: List<TerritoryDefinition>,
    val continents: List<ContinentDefinition>,
) {
    val territoriesById: Map<TerritoryId, TerritoryDefinition> = territories.associateBy(TerritoryDefinition::territoryId)
    val continentsById: Map<ContinentId, ContinentDefinition> = continents.associateBy(ContinentDefinition::continentId)
    val identifier: MapIdentifier = MapDefinitionHashing.identifierOf(this)
    val mapHash: String = identifier.mapHash
}

/**
 * Validiertes Territorium einer Map.
 */
data class TerritoryDefinition(
    val territoryId: TerritoryId,
    val edges: List<TerritoryEdgeDefinition>,
)

/**
 * Validierte Verbindung zwischen zwei Territorien.
 */
data class TerritoryEdgeDefinition(
    val targetId: TerritoryId,
)

/**
 * Validierter Kontinent einer Map.
 */
data class ContinentDefinition(
    val continentId: ContinentId,
    val territoryIds: List<TerritoryId>,
    val bonusValue: Int,
)
