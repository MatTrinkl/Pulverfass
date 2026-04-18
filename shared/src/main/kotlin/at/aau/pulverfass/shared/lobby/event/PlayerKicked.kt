package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Signalisiert, dass ein Spieler von einem Owner aus einer Lobby geworfen wurde.
 *
 * @property lobbyCode betroffene Lobby
 * @property targetPlayerId Spieler, der geworfen wurde
 * @property requesterPlayerId Spieler, der gekickt hat (muss Owner sein)
 */
data class PlayerKicked(
    override val lobbyCode: LobbyCode,
    val targetPlayerId: PlayerId,
    val requesterPlayerId: PlayerId,
) : ExternalLobbyEvent
