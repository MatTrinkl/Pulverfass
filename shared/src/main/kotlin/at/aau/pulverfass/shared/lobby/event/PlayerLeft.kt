package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Signalisiert, dass ein Spieler eine Lobby verlassen hat.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId ausgetretener Spieler
 * @property reason optionale technische oder fachliche Austrittsursache
 */
data class PlayerLeft(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val reason: String? = null,
) : ExternalLobbyEvent
