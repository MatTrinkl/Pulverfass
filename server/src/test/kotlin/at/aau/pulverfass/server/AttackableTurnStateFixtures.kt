package at.aau.pulverfass.server

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TerritoryState

internal fun GameState.withAttackableTerritories(players: List<PlayerId>): GameState {
    if (players.isEmpty()) {
        return this
    }

    val seededTerritories =
        territoryStates.toMutableMap().apply {
            when {
                players.size >= 3 -> {
                    put(TerritoryId("argentinien"), TerritoryState(TerritoryId("argentinien"), players[0], 3))
                    put(TerritoryId("brasilien"), TerritoryState(TerritoryId("brasilien"), players[1], 3))
                    put(TerritoryId("sahara"), TerritoryState(TerritoryId("sahara"), players[2], 3))
                }

                players.size >= 2 -> {
                    put(TerritoryId("argentinien"), TerritoryState(TerritoryId("argentinien"), players[0], 3))
                    put(TerritoryId("brasilien"), TerritoryState(TerritoryId("brasilien"), players[1], 3))
                }

                else -> {
                    put(TerritoryId("argentinien"), TerritoryState(TerritoryId("argentinien"), players[0], 3))
                }
            }
        }

    return copy(territoryStates = seededTerritories)
}
