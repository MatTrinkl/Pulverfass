package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DefaultMapCommandRuleServiceTest {
    private val ruleService = DefaultMapCommandRuleService()

    @Test
    fun `place troops valid creates troop update event`() {
        val playerOne = PlayerId(1)
        val baseState = sampleState()
        val state =
            baseState.copy(
                territoryStates =
                    baseState.territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(
                                    territoryId = TerritoryId("alpha"),
                                    ownerId = playerOne,
                                    troopCount = 3,
                                ),
                        ),
            )

        val events =
            ruleService.createEvents(
                state = state,
                command =
                    PlaceTroopsCommand(
                        lobbyCode = state.lobbyCode,
                        playerId = playerOne,
                        territoryId = TerritoryId("alpha"),
                        troopCount = 4,
                    ),
            )

        assertEquals(
            listOf(
                TerritoryTroopsChangedEvent(
                    lobbyCode = state.lobbyCode,
                    territoryId = TerritoryId("alpha"),
                    troopCount = 7,
                ),
            ),
            events,
        )
    }

    @Test
    fun `place troops invalid returns clear error`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val baseState = sampleState()
        val state =
            baseState.copy(
                territoryStates =
                    baseState.territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(
                                    territoryId = TerritoryId("alpha"),
                                    ownerId = playerTwo,
                                    troopCount = 3,
                                ),
                        ),
            )

        val exception =
            assertThrows(InvalidMapCommandException::class.java) {
                ruleService.createEvents(
                    state = state,
                    command =
                        PlaceTroopsCommand(
                            lobbyCode = state.lobbyCode,
                            playerId = playerOne,
                            territoryId = TerritoryId("alpha"),
                            troopCount = 1,
                        ),
                )
            }

        assertEquals(
            "Territory 'alpha' gehört nicht Spieler '1', sondern '2'.",
            exception.message,
        )
    }

    @Test
    fun `move troops valid creates deterministic source and target events`() {
        val playerOne = PlayerId(1)
        val state =
            sampleState().copy(
                territoryStates =
                    sampleState().territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(
                                    territoryId = TerritoryId("alpha"),
                                    ownerId = playerOne,
                                    troopCount = 5,
                                ),
                            TerritoryId("beta") to
                                TerritoryState(
                                    territoryId = TerritoryId("beta"),
                                    ownerId = playerOne,
                                    troopCount = 2,
                                ),
                        ),
            )

        val events =
            ruleService.createEvents(
                state = state,
                command =
                    MoveTroopsCommand(
                        lobbyCode = state.lobbyCode,
                        playerId = playerOne,
                        fromTerritoryId = TerritoryId("alpha"),
                        toTerritoryId = TerritoryId("beta"),
                        troopCount = 3,
                    ),
            )

        assertEquals(
            listOf(
                TerritoryTroopsChangedEvent(state.lobbyCode, TerritoryId("alpha"), 2),
                TerritoryTroopsChangedEvent(state.lobbyCode, TerritoryId("beta"), 5),
            ),
            events,
        )
    }

    @Test
    fun `move troops rejects non adjacent territories`() {
        val playerOne = PlayerId(1)
        val state =
            sampleState().copy(
                territoryStates =
                    sampleState().territoryStates +
                        mapOf(
                            TerritoryId("beta") to
                                TerritoryState(
                                    territoryId = TerritoryId("beta"),
                                    ownerId = playerOne,
                                    troopCount = 4,
                                ),
                            TerritoryId("gamma") to
                                TerritoryState(
                                    territoryId = TerritoryId("gamma"),
                                    ownerId = playerOne,
                                    troopCount = 2,
                                ),
                        ),
            )

        val exception =
            assertThrows(InvalidMapCommandException::class.java) {
                ruleService.createEvents(
                    state = state,
                    command =
                        MoveTroopsCommand(
                            lobbyCode = state.lobbyCode,
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("beta"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )
            }

        assertEquals(
            "Move von 'beta' nach 'gamma' ist nur für direkt benachbarte Territorien erlaubt.",
            exception.message,
        )
    }

    @Test
    fun `attack command creates deterministic conquest events`() {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val state =
            sampleState().copy(
                territoryStates =
                    sampleState().territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(
                                    territoryId = TerritoryId("alpha"),
                                    ownerId = attacker,
                                    troopCount = 5,
                                ),
                            TerritoryId("beta") to
                                TerritoryState(
                                    territoryId = TerritoryId("beta"),
                                    ownerId = defender,
                                    troopCount = 2,
                                ),
                        ),
            )

        val events =
            ruleService.createEvents(
                state = state,
                command =
                    AttackCommand(
                        lobbyCode = state.lobbyCode,
                        playerId = attacker,
                        fromTerritoryId = TerritoryId("alpha"),
                        toTerritoryId = TerritoryId("beta"),
                        attackerLosses = 1,
                        defenderLosses = 2,
                        occupyingTroopCount = 2,
                    ),
            )

        assertEquals(
            listOf(
                TerritoryTroopsChangedEvent(state.lobbyCode, TerritoryId("alpha"), 2),
                TerritoryOwnerChangedEvent(state.lobbyCode, TerritoryId("beta"), attacker),
                TerritoryTroopsChangedEvent(state.lobbyCode, TerritoryId("beta"), 2),
            ),
            events,
        )
    }

    private fun sampleState(): GameState =
        GameState.initial(
            lobbyCode = LobbyCode("CM12"),
            mapDefinition = sampleMapDefinition(),
            players = listOf(PlayerId(1), PlayerId(2)),
        )

    private fun sampleMapDefinition(): MapDefinition =
        MapDefinition(
            schemaVersion = 1,
            territories =
                listOf(
                    TerritoryDefinition(
                        territoryId = TerritoryId("alpha"),
                        edges =
                            listOf(
                                TerritoryEdgeDefinition(targetId = TerritoryId("beta")),
                                TerritoryEdgeDefinition(targetId = TerritoryId("gamma")),
                            ),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("beta"),
                        edges = listOf(TerritoryEdgeDefinition(targetId = TerritoryId("alpha"))),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("gamma"),
                        edges = listOf(TerritoryEdgeDefinition(targetId = TerritoryId("alpha"))),
                    ),
                ),
            continents =
                listOf(
                    ContinentDefinition(
                        continentId = ContinentId("north"),
                        territoryIds = listOf(TerritoryId("alpha"), TerritoryId("beta")),
                        bonusValue = 3,
                    ),
                ),
        )
}
