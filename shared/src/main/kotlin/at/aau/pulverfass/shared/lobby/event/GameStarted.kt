package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Signalisiert, dass ein Spiel gestartet wurde.
 *
 * @property lobbyCode betroffene Lobby
 * @property randomSeed serverseitig erzeugter Seed für die zufällige Startvorbereitung
 */
data class GameStarted(
    override val lobbyCode: LobbyCode,
    val randomSeed: Long = 0L,
) : ExternalLobbyEvent
