package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Signalisiert, dass eine Lobby technisch angelegt wurde.
 *
 * @property lobbyCode neu erzeugte Lobby
 */
data class LobbyCreated(
    override val lobbyCode: LobbyCode,
) : InternalLobbyEvent
