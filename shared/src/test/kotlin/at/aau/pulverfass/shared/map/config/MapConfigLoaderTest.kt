package at.aau.pulverfass.shared.map.config

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.TerritoryId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MapConfigLoaderTest {
    @Test
    fun `valid config lädt aus default resource`() {
        val definition = MapConfigLoader.loadDefault()

        assertEquals(MapConfig.CURRENT_SCHEMA_VERSION, definition.schemaVersion)
        assertEquals(23, definition.territories.size)
        assertEquals(6, definition.continents.size)
        assertNotNull(definition.territoriesById[TerritoryId("argentinien")])
        assertTrue(
            definition.territoriesById
                .getValue(TerritoryId("brasilien"))
                .edges
                .any { it.targetId == TerritoryId("sahara") },
        )
    }

    @Test
    fun `edges schema load test`() {
        val definition = MapConfigLoader.loadFromJson(validEdgesJson())

        val alphaEdges = definition.territoriesById.getValue(TerritoryId("alpha")).edges

        assertEquals(2, alphaEdges.size)
        assertTrue(alphaEdges.any { it.targetId == TerritoryId("beta") })
        assertTrue(alphaEdges.any { it.targetId == TerritoryId("gamma") })
    }

    @Test
    fun `legacy load test adjacentTerritoryIds werden als edges interpretiert`() {
        val definition = MapConfigLoader.loadFromJson(validLegacyJson())

        val alphaEdges = definition.territoriesById.getValue(TerritoryId("alpha")).edges

        assertEquals(2, alphaEdges.size)
        assertTrue(alphaEdges.map { it.targetId }.containsAll(listOf(TerritoryId("beta"), TerritoryId("gamma"))))
    }

    @Test
    fun `duplicate territoryId führt zu fail`() {
        val exception =
            assertThrows<MapConfigValidationException> {
                MapConfigLoader.loadFromJson(
                    """
                    {
                      "schemaVersion": 1,
                      "territories": [
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "beta", "edges": [{ "targetId": "alpha" }] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonusValue": 2 }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        assertTrue(exception.message.orEmpty().contains("mehrfach definiert"))
    }

    @Test
    fun `unknown adjacent id führt zu fail`() {
        val exception =
            assertThrows<MapConfigValidationException> {
                MapConfigLoader.loadFromJson(
                    """
                    {
                      "schemaVersion": 1,
                      "territories": [
                        { "territoryId": "alpha", "adjacentTerritoryIds": ["beta", "missing"] },
                        { "territoryId": "beta", "adjacentTerritoryIds": ["alpha"] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonusValue": 2 }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        assertTrue(exception.message.orEmpty().contains("unbekanntes Ziel-Territory 'missing'"))
    }

    @Test
    fun `asymmetrische adjacency führt zu fail`() {
        val exception =
            assertThrows<MapConfigValidationException> {
                MapConfigLoader.loadFromJson(
                    """
                    {
                      "schemaVersion": 1,
                      "territories": [
                        { "territoryId": "alpha", "adjacentTerritoryIds": ["beta"] },
                        { "territoryId": "beta", "adjacentTerritoryIds": [] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonusValue": 2 }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        assertTrue(exception.message.orEmpty().contains("keine Reverse-Edge"))
    }

    @Test
    fun `continent referenziert unknown territory führt zu fail`() {
        val exception =
            assertThrows<MapConfigValidationException> {
                MapConfigLoader.loadFromJson(
                    """
                    {
                      "schemaVersion": 1,
                      "territories": [
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "beta", "edges": [{ "targetId": "alpha" }] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "missing"], "bonusValue": 2 }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        assertTrue(exception.message.orEmpty().contains("referenziert unbekanntes Territory 'missing'"))
    }

    @Test
    fun `validator test für fehlende reverse edge`() {
        val exception =
            assertThrows<MapConfigValidationException> {
                MapConfigLoader.loadFromJson(
                    """
                    {
                      "schemaVersion": 1,
                      "territories": [
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "beta", "edges": [{ "targetId": "gamma" }] },
                        { "territoryId": "gamma", "edges": [{ "targetId": "beta" }] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "beta", "gamma"], "bonusValue": 3 }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        assertTrue(exception.message.orEmpty().contains("alpha"))
        assertTrue(exception.message.orEmpty().contains("beta"))
        assertTrue(exception.message.orEmpty().contains("keine Reverse-Edge"))
    }

    @Test
    fun `doppelte edges werden abgewiesen`() {
        val exception =
            assertThrows<MapConfigValidationException> {
                MapConfigLoader.loadFromJson(
                    """
                    {
                      "schemaVersion": 1,
                      "territories": [
                        {
                          "territoryId": "alpha",
                          "edges": [
                            { "targetId": "beta" },
                            { "targetId": "beta" }
                          ]
                        },
                        {
                          "territoryId": "beta",
                          "edges": [{ "targetId": "alpha" }]
                        }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonusValue": 2 }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        assertTrue(exception.message.orEmpty().contains("mehrfach"))
    }

    @Test
    fun `territory darf nicht in mehreren continents sein`() {
        val exception =
            assertThrows<MapConfigValidationException> {
                MapConfigLoader.loadFromJson(
                    """
                    {
                      "schemaVersion": 1,
                      "territories": [
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "beta", "edges": [{ "targetId": "alpha" }] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha"], "bonusValue": 1 },
                        { "continentId": "south", "territoryIds": ["alpha", "beta"], "bonusValue": 1 }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        assertTrue(exception.message.orEmpty().contains("mehreren Continents"))
    }

    @Test
    fun `continent lookup wird aufgebaut`() {
        val definition = MapConfigLoader.loadFromJson(validEdgesJson())

        val continent = definition.continentsById[ContinentId("north")]

        assertNotNull(continent)
        assertEquals(listOf(TerritoryId("alpha"), TerritoryId("beta")), continent?.territoryIds)
    }

    private fun validEdgesJson(): String =
        """
        {
          "schemaVersion": 1,
          "territories": [
            {
              "territoryId": "alpha",
              "edges": [
                { "targetId": "beta" },
                { "targetId": "gamma" }
              ]
            },
            {
              "territoryId": "beta",
              "edges": [
                { "targetId": "alpha" }
              ]
            },
            {
              "territoryId": "gamma",
              "edges": [
                { "targetId": "alpha" }
              ]
            }
          ],
          "continents": [
            { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonusValue": 2 },
            { "continentId": "south", "territoryIds": ["gamma"], "bonusValue": 1 }
          ]
        }
        """.trimIndent()

    private fun validLegacyJson(): String =
        """
        {
          "schemaVersion": 1,
          "territories": [
            {
              "territoryId": "alpha",
              "adjacentTerritoryIds": ["beta", "gamma"]
            },
            {
              "territoryId": "beta",
              "adjacentTerritoryIds": ["alpha"]
            },
            {
              "territoryId": "gamma",
              "adjacentTerritoryIds": ["alpha"]
            }
          ],
          "continents": [
            { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonusValue": 2 },
            { "continentId": "south", "territoryIds": ["gamma"], "bonusValue": 1 }
          ]
        }
        """.trimIndent()
}
