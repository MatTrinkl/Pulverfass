package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GameStartPreparationTest {
    @Test
    fun `prepare rejects lobbies with fewer than three players`() {
        val mapDefinition = MapConfigLoader.loadDefault()
        val state =
            GameState.initial(
                lobbyCode = LobbyCode("GP10"),
                mapDefinition = mapDefinition,
                players = listOf(PlayerId(1), PlayerId(2)),
            )

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                GameStartPreparation.prepare(state, randomSeed = 123L)
            }

        assertEquals(
            "Spielstart ist nur für 3 bis 6 Spieler unterstützt, waren aber 2.",
            exception.message,
        )
    }

    @Test
    fun `prepare gives three players 20 troops each without continent bonus`() {
        val players = listOf(PlayerId(1), PlayerId(2), PlayerId(3))
        val mapDefinition = MapConfigLoader.loadDefault()
        val state =
            GameState.initial(
                lobbyCode = LobbyCode("GP30"),
                mapDefinition = mapDefinition,
                players = players,
            )

        val prepared = GameStartPreparation.prepare(state, randomSeed = 456L)
        val startedState = state.copy(territoryStates = prepared.preparedTerritoryStates)

        players.forEach { playerId ->
            assertEquals(20, troopsOf(playerId, prepared.preparedTerritoryStates.values))
            assertEquals(0, startedState.continentsOwnedBy(playerId).size)
        }
        assertEquals(
            60,
            prepared.preparedTerritoryStates.values.sumOf(TerritoryState::troopCount),
        )
        assertEquals(
            true,
            prepared.preparedTerritoryStates.values.all { territoryState ->
                territoryState.ownerId != null && territoryState.troopCount in 1..4
            },
        )
    }

    private fun troopsOf(
        playerId: PlayerId,
        territoryStates: Collection<TerritoryState>,
    ): Int =
        territoryStates
            .filter { territoryState -> territoryState.ownerId == playerId }
            .sumOf(TerritoryState::troopCount)
}
