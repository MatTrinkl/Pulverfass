package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Signalisiert einen internen Taktimpuls für eine Lobby.
 *
 * @property lobbyCode betroffene Lobby
 * @property tick monotoner Tick-Zähler innerhalb der Lobbyverarbeitung
 */
data class SystemTick(
    override val lobbyCode: LobbyCode,
    val tick: Long,
) : InternalLobbyEvent {
    init {
        require(tick >= 0) { "SystemTick.tick darf nicht negativ sein, war aber $tick." }
    }
}
