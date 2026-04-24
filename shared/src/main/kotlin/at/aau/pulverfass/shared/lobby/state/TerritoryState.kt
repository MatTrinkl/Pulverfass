package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId

/**
 * Mutierbarer Laufzeitzustand eines Territoriums innerhalb des GameStates.
 *
 * Die topologische Struktur des Territoriums liegt ausschließlich in der
 * readonly Map-Definition. Dieser Typ hält nur zustandsbehaftete Informationen.
 */
data class TerritoryState(
    val territoryId: TerritoryId,
    val ownerId: PlayerId? = null,
    val troopCount: Int = 0,
) {
    init {
        require(troopCount >= 0) {
            "TerritoryState.troopCount darf nicht negativ sein, war aber $troopCount."
        }
    }
}
