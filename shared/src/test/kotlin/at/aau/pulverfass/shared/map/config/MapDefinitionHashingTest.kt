package at.aau.pulverfass.shared.map.config

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapDefinitionHashingTest {
    @Test
    fun `hash bleibt gleich bei identischer config trotz whitespace und feldreihenfolge`() {
        val compact =
            MapConfigLoader.loadFromJson(
                """
                {
                  "schemaVersion": 1,
                  "territories": [
                    { "territoryId": "alpha", "edges": [{ "targetId": "beta" }] },
                    { "territoryId": "beta", "edges": [{ "targetId": "alpha" }] }
                  ],
                  "continents": [
                    { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonusValue": 2 }
                  ]
                }
                """.trimIndent(),
            )
        val reformatted =
            MapConfigLoader.loadFromJson(
                """
                {
                  "continents": [
                    {
                      "bonusValue": 2,
                      "territoryIds": ["alpha", "beta"],
                      "continentId": "north"
                    }
                  ],
                  "territories": [
                    {
                      "edges": [
                        { "targetId": "beta" }
                      ],
                      "territoryId": "alpha"
                    },
                    {
                      "territoryId": "beta",
                      "edges": [{ "targetId": "alpha" }]
                    }
                  ],
                  "schemaVersion": 1
                }
                """.trimIndent(),
            )

        assertEquals(compact.mapHash, reformatted.mapHash)
        assertEquals(compact.identifier, reformatted.identifier)
    }

    @Test
    fun `hash ändert sich bei configänderung`() {
        val baseDefinition = MapConfigLoader.loadFromJson(validEdgesJson())
        val changedDefinition =
            MapConfigLoader.loadFromJson(
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
                        { "targetId": "alpha" },
                        { "targetId": "gamma" }
                      ]
                    },
                    {
                      "territoryId": "gamma",
                      "edges": [
                        { "targetId": "alpha" },
                        { "targetId": "beta" }
                      ]
                    }
                  ],
                  "continents": [
                    { "continentId": "north", "territoryIds": ["alpha", "beta"], "bonusValue": 2 },
                    { "continentId": "south", "territoryIds": ["gamma"], "bonusValue": 1 }
                  ]
                }
                """.trimIndent(),
            )

        assertNotEquals(baseDefinition.mapHash, changedDefinition.mapHash)
    }

    @Test
    fun `canonical json sortiert semantisch gleiche definitionen identisch`() {
        val first =
            MapDefinition(
                schemaVersion = 1,
                territories =
                    listOf(
                        TerritoryDefinition(
                            territoryId = TerritoryId("beta"),
                            edges = listOf(TerritoryEdgeDefinition(TerritoryId("alpha"))),
                        ),
                        TerritoryDefinition(
                            territoryId = TerritoryId("alpha"),
                            edges = listOf(TerritoryEdgeDefinition(TerritoryId("beta"))),
                        ),
                    ),
                continents =
                    listOf(
                        ContinentDefinition(
                            continentId = ContinentId("north"),
                            territoryIds = listOf(TerritoryId("beta"), TerritoryId("alpha")),
                            bonusValue = 2,
                        ),
                    ),
            )
        val second =
            MapDefinition(
                schemaVersion = 1,
                territories =
                    listOf(
                        TerritoryDefinition(
                            territoryId = TerritoryId("alpha"),
                            edges = listOf(TerritoryEdgeDefinition(TerritoryId("beta"))),
                        ),
                        TerritoryDefinition(
                            territoryId = TerritoryId("beta"),
                            edges = listOf(TerritoryEdgeDefinition(TerritoryId("alpha"))),
                        ),
                    ),
                continents =
                    listOf(
                        ContinentDefinition(
                            continentId = ContinentId("north"),
                            territoryIds = listOf(TerritoryId("alpha"), TerritoryId("beta")),
                            bonusValue = 2,
                        ),
                    ),
            )

        assertEquals(MapDefinitionHashing.canonicalJson(first), MapDefinitionHashing.canonicalJson(second))
        assertEquals(first.mapHash, second.mapHash)
    }

    @Test
    fun `map get response enthält identifier aus definition`() {
        val definition = MapConfigLoader.loadFromJson(validEdgesJson())
        val response =
            MapGetResponse.fromGameState(
                GameState.initial(
                    lobbyCode = LobbyCode("AB12"),
                    mapDefinition = definition,
                    players = listOf(PlayerId(1)),
                ),
            )

        assertEquals(definition.schemaVersion, response.schemaVersion)
        assertEquals(definition.mapHash, response.mapHash)
        assertEquals(0, response.stateVersion)
        assertTrue(response.mapHash.length == 64)
    }

    @Test
    fun `golden test default map hash stable`() {
        val definition = MapConfigLoader.loadDefault()

        assertEquals(
            "1f199cdfdf124a7f72030f2f0144c601a1246681a08c5fadc7377b67890b542b",
            definition.mapHash,
        )
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
}
