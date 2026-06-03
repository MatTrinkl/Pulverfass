package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId

/**
 * Domain-Event für einen erfolgreich angewendeten Fortify-Zug.
 *
 * Das Event wird im Shared-Modul definiert, damit Reducer, Tests und Backend dieselbe
 * serialisierbare Beschreibung des Truppenverschubs verwenden.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId Spieler, der den Zug ausgeführt hat
 * @property fromTerritoryId Ursprungsterritorium des Truppenverschubs
 * @property toTerritoryId Zielterritorium des Truppenverschubs
 * @property troopCount verschobene Truppenanzahl
 */
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
