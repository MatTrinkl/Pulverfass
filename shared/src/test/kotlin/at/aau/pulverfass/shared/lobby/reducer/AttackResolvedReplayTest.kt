package at.aau.pulverfass.shared.lobby.reducer

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.command.AttackCommand
import at.aau.pulverfass.shared.lobby.command.DefaultMapCommandRuleService
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AttackResolvedReplayTest {
    private val ruleService = DefaultMapCommandRuleService()
    private val reducer = DefaultLobbyEventReducer()

    @Test
    fun `battle replay consistency keeps resulting state identical`() {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val baseState =
            GameState.initial(
                lobbyCode = LobbyCode("AT12"),
                mapDefinition = sampleMapDefinition(),
                players = listOf(attacker, defender),
            ).copy(
                gameRandomSeed = 1L,
                gameRandomState = 2L,
                gameStarted = true,
                status = at.aau.pulverfass.shared.lobby.state.GameStatus.RUNNING,
                territoryStates =
                    mapOf(
                        TerritoryId("alpha") to TerritoryState(TerritoryId("alpha"), attacker, 5),
                        TerritoryId("beta") to TerritoryState(TerritoryId("beta"), defender, 2),
                    ),
            )

        val events =
            ruleService.createEvents(
                state = baseState,
                command =
                    AttackCommand(
                        lobbyCode = baseState.lobbyCode,
                        playerId = attacker,
                        fromTerritoryId = TerritoryId("alpha"),
                        toTerritoryId = TerritoryId("beta"),
                        requestedAttackDice = 3,
                        committedTroopCount = 3,
                        occupyingTroopCount = 3,
                    ),
            )
        val event = events[0] as AttackResolvedEvent
        val eliminationEvent = events[1] as PlayerEliminatedEvent

        val first = reducer.apply(baseState, event, context = null)
        val finalFirst = reducer.apply(first, eliminationEvent, context = null)
        val replayed = reducer.apply(baseState, event, context = null)
        val finalReplayed = reducer.apply(replayed, eliminationEvent, context = null)

        assertEquals(finalFirst, finalReplayed)
        assertEquals(attacker, finalFirst.ownerOf(TerritoryId("beta")))
        assertEquals(2, finalFirst.troopCountOf(TerritoryId("alpha")))
        assertEquals(3, finalFirst.troopCountOf(TerritoryId("beta")))
        assertEquals(listOf(attacker), finalFirst.turnOrder)
        assertEquals(event.rngStateAfter, finalFirst.gameRandomState)
    }

    private fun sampleMapDefinition(): MapDefinition =
        MapDefinition(
            schemaVersion = 1,
            territories =
                listOf(
                    TerritoryDefinition(
                        territoryId = TerritoryId("alpha"),
                        edges = listOf(TerritoryEdgeDefinition(targetId = TerritoryId("beta"))),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("beta"),
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
