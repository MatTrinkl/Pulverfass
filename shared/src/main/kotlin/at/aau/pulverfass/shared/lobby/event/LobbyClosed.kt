package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Signalisiert, dass eine Lobby geschlossen wurde.
 *
 * @property lobbyCode geschlossene Lobby
 * @property reason optionale fachliche oder technische Schließursache
 */
data class LobbyClosed(
    override val lobbyCode: LobbyCode,
    val reason: String? = null,
) : InternalLobbyEvent
