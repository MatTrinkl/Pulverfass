package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Setzt den ausstehenden Verstärkungspool eines Spielers auf einen absoluten Wert.
 */
data class PendingReinforcementsSetEvent(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val amount: Int,
) : InternalLobbyEvent {
    init {
        require(amount >= 0) {
            "PendingReinforcementsSetEvent.amount darf nicht negativ sein, war aber $amount."
        }
    }
}
