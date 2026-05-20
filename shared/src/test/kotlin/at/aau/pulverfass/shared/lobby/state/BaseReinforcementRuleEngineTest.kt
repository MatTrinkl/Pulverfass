package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BaseReinforcementRuleEngineTest {
    private val reducer = DefaultLobbyEventReducer()

    @Test
    fun `owned smaller than nine yields minimum territory bonus of three`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val lobbyCode = LobbyCode("RB13")
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = reinforcementMapDefinition(),
                players = listOf(playerOne, playerTwo),
            )

        val ownedState =
            listOf("alpha")
                .fold(baseState) { state, territoryId ->
                    reducer.apply(
                        state,
                        TerritoryOwnerChangedEvent(lobbyCode, TerritoryId(territoryId), playerOne),
                    )
                }

        val breakdown =
            BaseReinforcementRuleEngine.computeBaseReinforcements(playerOne, ownedState)

        assertEquals(1, ownedState.ownedTerritoryCount(playerOne))
        assertEquals(3, breakdown.territoryBonus)
        assertEquals(0, breakdown.continentBonus)
        assertEquals(3, breakdown.total)
    }

    @Test
    fun `continent ownership contributes configured sum deterministically`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val lobbyCode = LobbyCode("RB14")
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = reinforcementMapDefinition(),
                players = listOf(playerOne, playerTwo),
            )

        val ownedState =
            listOf("alpha", "beta", "gamma")
                .fold(baseState) { state, territoryId ->
                    reducer.apply(
                        state,
                        TerritoryOwnerChangedEvent(lobbyCode, TerritoryId(territoryId), playerOne),
                    )
                }

        val breakdown =
            BaseReinforcementRuleEngine.computeBaseReinforcements(playerOne, ownedState)

        assertEquals(true, ownedState.ownsContinent(playerOne, ContinentId("north")))
        assertEquals(3, ownedState.continentBonus(ContinentId("north")))
        assertEquals(1, ownedState.continentBonus(ContinentId("south")))
        assertEquals(3, breakdown.territoryBonus)
        assertEquals(4, breakdown.continentBonus)
        assertEquals(7, breakdown.total)
    }

    private fun reinforcementMapDefinition(): MapDefinition =
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
                        edges =
                            listOf(
                                TerritoryEdgeDefinition(TerritoryId("alpha")),
                                TerritoryEdgeDefinition(TerritoryId("gamma")),
                            ),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("gamma"),
                        edges = listOf(TerritoryEdgeDefinition(TerritoryId("beta"))),
                    ),
                ),
            continents =
                listOf(
                    ContinentDefinition(
                        continentId = ContinentId("north"),
                        territoryIds = listOf(TerritoryId("alpha"), TerritoryId("beta")),
                        bonusValue = 3,
                    ),
                    ContinentDefinition(
                        continentId = ContinentId("south"),
                        territoryIds = listOf(TerritoryId("gamma")),
                        bonusValue = 1,
                    ),
                ),
        )
}
