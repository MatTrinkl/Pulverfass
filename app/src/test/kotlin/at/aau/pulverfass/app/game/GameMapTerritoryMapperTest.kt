package at.aau.pulverfass.app.game

import at.aau.pulverfass.app.ui.map.PulverfassMapDefaults
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import kotlin.test.Test
import kotlin.test.assertTrue

class GameMapTerritoryMapperTest {
    @Test
    fun `default map territories are mapped to Android regions`() {
        val androidRegionIds = PulverfassMapDefaults.regions.map { region -> region.id }.toSet()
        val unmappedTerritories =
            MapConfigLoader
                .loadDefault()
                .territories
                .map { territory -> territory.territoryId }
                .filter { territoryId ->
                    GameMapTerritoryMapper.toAndroidRegionId(territoryId) !in androidRegionIds
                }

        assertTrue(
            actual = unmappedTerritories.isEmpty(),
            message =
                "Nicht gemappte TerritoryIds: " +
                    unmappedTerritories.joinToString { territoryId -> territoryId.value },
        )
    }

    @Test
    fun `mapped territories roundtrip through Android region ids`() {
        val roundtripFailures =
            MapConfigLoader
                .loadDefault()
                .territories
                .map { territory -> territory.territoryId }
                .filter { territoryId ->
                    val regionId = GameMapTerritoryMapper.toAndroidRegionId(territoryId)
                    regionId == null ||
                        GameMapTerritoryMapper.toTerritoryId(regionId) != territoryId
                }

        assertTrue(
            actual = roundtripFailures.isEmpty(),
            message =
                "Nicht reversible TerritoryMappings: " +
                    roundtripFailures.joinToString { territoryId -> territoryId.value },
        )
    }
}
