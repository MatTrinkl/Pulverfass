package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId

data class FortifyMoveAppliedEvent(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val fromTerritoryId: TerritoryId,
    val toTerritoryId: TerritoryId,
    val troopCount: Int,
) : ExternalLobbyEvent {
    init {
        require(troopCount > 0) {
            "FortifyMoveAppliedEvent.troopCount muss positiv sein, war aber $troopCount."
        }
        require(fromTerritoryId != toTerritoryId) {
            "FortifyMoveAppliedEvent benötigt unterschiedliche Territorien."
        }
    }
}
