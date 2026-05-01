package at.aau.pulverfass.shared.map.config

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.TerritoryId
import kotlinx.serialization.Serializable

/**
 * Serialisierbare Rohkonfiguration einer Spielmap.
 *
 * Das Format ist JSON-basiert und unterstützt übergangsweise sowohl `edges`
 * als auch das Legacy-Feld `adjacentTerritoryIds`.
 */
@Serializable
data class MapConfig(
    val schemaVersion: Int,
    val territories: List<TerritoryConfig>,
    val continents: List<ContinentConfig>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/**
 * Rohkonfiguration eines Territoriums.
 */
@Serializable
data class TerritoryConfig(
    val territoryId: TerritoryId,
    val edges: List<TerritoryEdgeConfig> = emptyList(),
    val adjacentTerritoryIds: List<TerritoryId> = emptyList(),
)

/**
 * Rohkonfiguration einer gerichteten Nachbarschaftskante.
 */
@Serializable
data class TerritoryEdgeConfig(
    val targetId: TerritoryId,
)

/**
 * Rohkonfiguration eines Kontinents.
 */
@Serializable
data class ContinentConfig(
    val continentId: ContinentId,
    val territoryIds: List<TerritoryId>,
    val bonusValue: Int,
) {
    init {
        require(bonusValue >= 0) {
            "ContinentConfig.bonusValue darf nicht negativ sein, war aber $bonusValue."
        }
    }
}
