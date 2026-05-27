package at.aau.pulverfass.shared.map.config

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.TerritoryId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream

class MapConfigLoaderTest {
    @Test
    fun `valid config lädt aus default resource`() {
        val definition = MapConfigLoader.loadDefault()

        assertEquals(MapConfig.CURRENT_SCHEMA_VERSION, definition.schemaVersion)
        assertEquals(24, definition.territories.size)
        assertEquals(6, definition.continents.size)
        assertEquals(3, definition.continentsById.getValue(ContinentId("nordamerika")).bonusValue)
        assertEquals(2, definition.continentsById.getValue(ContinentId("europa")).bonusValue)
        assertEquals(4, definition.continentsById.getValue(ContinentId("asien")).bonusValue)
        assertEquals(2, definition.continentsById.getValue(ContinentId("suedamerika")).bonusValue)
        assertEquals(2, definition.continentsById.getValue(ContinentId("afrika")).bonusValue)
        assertEquals(1, definition.continentsById.getValue(ContinentId("ozeanien")).bonusValue)
        assertTrue(
            TerritoryId("alaska") !in
                definition.continentsById.getValue(ContinentId("nordamerika")).territoryIds,
        )
        assertTrue(
            TerritoryId("alaska") in
                definition.continentsById.getValue(ContinentId("asien")).territoryIds,
        )
        assertTrue(
            definition.territoriesById
                .getValue(TerritoryId("alaska"))
                .edges
                .any { it.targetId == TerritoryId("japan") },
        )
        assertTrue(
            definition.territoriesById
                .getValue(TerritoryId("japan"))
                .edges
                .any { it.targetId == TerritoryId("alaska") },
        )
        assertTrue(
            definition.territoriesById
                .getValue(TerritoryId("sibirien"))
                .edges
                .none { it.targetId == TerritoryId("japan") },
        )
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
        assertTrue(
            alphaEdges.map {
                it.targetId
            }.containsAll(listOf(TerritoryId("beta"), TerritoryId("gamma"))),
        )
    }

    @Test
    fun `duplicate territoryId führt zu fail`() {
        val exception =
            assertThrows<MapConfigValidationException> {
                MapConfigLoader.loadFromJson(
                    """
                    {
                      "schemaVersion": 2,
                      "territories": [
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "beta", "edges": [{ "targetId": "alpha" }] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonus": 2 }
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
                      "schemaVersion": 2,
                      "territories": [
                        { "territoryId": "alpha", "adjacentTerritoryIds": ["beta", "missing"] },
                        { "territoryId": "beta", "adjacentTerritoryIds": ["alpha"] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonus": 2 }
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
                      "schemaVersion": 2,
                      "territories": [
                        { "territoryId": "alpha", "adjacentTerritoryIds": ["beta"] },
                        { "territoryId": "beta", "adjacentTerritoryIds": [] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonus": 2 }
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
                      "schemaVersion": 2,
                      "territories": [
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "beta", "edges": [{ "targetId": "alpha" }] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "missing"], "bonus": 2 }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        assertTrue(
            exception.message.orEmpty().contains("referenziert unbekanntes Territory 'missing'"),
        )
    }

    @Test
    fun `validator test für fehlende reverse edge`() {
        val exception =
            assertThrows<MapConfigValidationException> {
                MapConfigLoader.loadFromJson(
                    """
                    {
                      "schemaVersion": 2,
                      "territories": [
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "beta", "edges": [{ "targetId": "gamma" }] },
                        { "territoryId": "gamma", "edges": [{ "targetId": "beta" }] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "beta", "gamma"], "bonus": 3 }
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
                      "schemaVersion": 2,
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
                        { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonus": 2 }
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
                      "schemaVersion": 2,
                      "territories": [
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "beta", "edges": [{ "targetId": "alpha" }] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha"], "bonus": 1 },
                        { "continentId": "south", "territoryIds": ["alpha", "beta"], "bonus": 1 }
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

    @Test
    fun `load liest JSON aus input stream`() {
        val definition =
            MapConfigLoader.load(
                ByteArrayInputStream(validEdgesJson().encodeToByteArray()),
            )

        assertEquals(MapConfig.CURRENT_SCHEMA_VERSION, definition.schemaVersion)
        assertEquals(3, definition.territories.size)
    }

    @Test
    fun `loadResource normalisiert fuehrenden slash`() {
        val definition =
            MapConfigLoader.loadResource(
                "/${MapConfigLoader.DEFAULT_RESOURCE_PATH}",
            )

        assertEquals(24, definition.territories.size)
    }

    @Test
    fun `fehlende resource wird als load exception gemeldet`() {
        val exception =
            assertThrows<MapConfigLoadException> {
                MapConfigLoader.loadResource("config/maps/does-not-exist.json")
            }

        assertTrue(exception.message.orEmpty().contains("wurde nicht gefunden"))
    }

    @Test
    fun `ungueltiges JSON wird als load exception mit cause gemeldet`() {
        val exception =
            assertThrows<MapConfigLoadException> {
                MapConfigLoader.loadFromJson("{ invalid json")
            }

        assertTrue(exception.message.orEmpty().contains("konnte nicht geparst werden"))
        assertNotNull(exception.cause)
    }

    @Test
    fun `negative continent bonus führt zu validation fail`() {
        val exception =
            assertThrows<MapConfigValidationException> {
                MapConfigLoader.loadFromJson(
                    """
                    {
                      "schemaVersion": 2,
                      "territories": [
                        { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                        { "territoryId": "beta", "edges": [{ "targetId": "alpha" }] }
                      ],
                      "continents": [
                        { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonus": -1 }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        assertTrue(exception.message.orEmpty().contains("negativen Bonus"))
    }

    @Test
    fun `config model defaults are explicit`() {
        val territory = TerritoryConfig(territoryId = TerritoryId("alpha"))
        val edge = TerritoryEdgeConfig(TerritoryId("beta"))
        val cause = IllegalStateException("source")
        val exception = MapConfigLoadException("load failed", cause)
        val continent =
            ContinentConfig(
                continentId = ContinentId("north"),
                territoryIds = listOf(TerritoryId("alpha")),
                bonus = 2,
            )

        assertTrue(territory.edges.isEmpty())
        assertTrue(territory.adjacentTerritoryIds.isEmpty())
        assertEquals(TerritoryId("beta"), edge.targetId)
        assertEquals(2, continent.bonus)
        assertSame(cause, exception.cause)
    }

    private fun validEdgesJson(): String =
        """
        {
          "schemaVersion": 2,
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
            { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonus": 2 },
            { "continentId": "south", "territoryIds": ["gamma"], "bonus": 1 }
          ]
        }
        """.trimIndent()

    private fun validLegacyJson(): String =
        """
        {
          "schemaVersion": 2,
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
            { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonus": 2 },
            { "continentId": "south", "territoryIds": ["gamma"], "bonus": 1 }
          ]
        }
        """.trimIndent()
}
