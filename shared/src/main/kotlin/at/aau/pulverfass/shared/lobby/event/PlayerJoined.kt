package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Signalisiert, dass ein Spieler einer Lobby beigetreten ist.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId beigetretener Spieler
 */
data class PlayerJoined(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
) : ExternalLobbyEvent
