package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Konfiguriert den Startspieler für eine Lobby vor Spielstart.
 *
 * @property lobbyCode betroffene Lobby
 * @property startPlayerId gewünschter Startspieler
 * @property requesterPlayerId Spieler, der die Änderung anfordert
 */
data class StartPlayerConfigured(
    override val lobbyCode: LobbyCode,
    val startPlayerId: PlayerId,
    val requesterPlayerId: PlayerId,
) : ExternalLobbyEvent
