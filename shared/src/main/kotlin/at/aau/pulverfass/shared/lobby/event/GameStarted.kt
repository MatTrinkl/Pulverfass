package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Signalisiert, dass ein Spiel gestartet wurde.
 *
 * @property lobbyCode betroffene Lobby
 */
data class GameStarted(
    override val lobbyCode: LobbyCode,
) : ExternalLobbyEvent
