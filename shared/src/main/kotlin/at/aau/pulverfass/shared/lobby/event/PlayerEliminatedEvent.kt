package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import kotlinx.serialization.Serializable

/**
 * Markiert einen Spieler als eliminiert, sobald er keine Territorien mehr besitzt.
 *
 * Eliminierte Spieler bleiben als Zuschauer Teil der Lobby, werden aber aus der
 * aktiven TurnOrder entfernt.
 */
@Serializable
data class PlayerEliminatedEvent(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val eliminatedByPlayerId: PlayerId,
    val stateVersion: Long? = null,
) : InternalLobbyEvent, PublicGameEvent {
    init {
        require(playerId != eliminatedByPlayerId) {
            "PlayerEliminatedEvent.playerId und eliminatedByPlayerId muessen verschieden sein."
        }
        require(stateVersion == null || stateVersion >= 0) {
            "PlayerEliminatedEvent.stateVersion darf nicht negativ sein."
        }
    }
}
