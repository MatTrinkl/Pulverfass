package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.PlayerId
import kotlin.math.max

/**
 * Deterministische Berechnung der Basis-Verstärkungen zu Beginn von Phase 1.
 */
object BaseReinforcementRuleEngine {
    fun computeBaseReinforcements(
        playerId: PlayerId,
        state: GameState,
    ): BaseReinforcementBreakdown {
        val ownedTerritories = state.ownedTerritoryCount(playerId)
        val territoryBonus = max(3, ownedTerritories / 3)
        val continentBonus =
            state
                .continentsOwnedBy(playerId)
                .sumOf(state::continentBonus)

        return BaseReinforcementBreakdown(
            territoryBonus = territoryBonus,
            continentBonus = continentBonus,
            total = territoryBonus + continentBonus,
        )
    }
}

data class BaseReinforcementBreakdown(
    val territoryBonus: Int,
    val continentBonus: Int,
    val total: Int,
)
