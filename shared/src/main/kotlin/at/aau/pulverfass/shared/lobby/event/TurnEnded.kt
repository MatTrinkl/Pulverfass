package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Signalisiert, dass ein Spieler seinen Zug beendet hat.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId Spieler, der den Zug beendet hat
 */
data class TurnEnded(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
) : ExternalLobbyEvent
