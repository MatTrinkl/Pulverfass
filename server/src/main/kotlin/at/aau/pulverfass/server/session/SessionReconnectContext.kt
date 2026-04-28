package at.aau.pulverfass.server.session

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Wiederherstellbarer Fachkontext einer technischen Session.
 */
data class SessionReconnectContext(
    val playerId: PlayerId? = null,
    val lobbyCode: LobbyCode? = null,
    val playerDisplayName: String? = null,
)
