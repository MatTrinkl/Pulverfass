package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Signalisiert, dass innerhalb einer Lobby ein Timeout ausgelöst wurde.
 *
 * @property lobbyCode betroffene Lobby
 * @property target logischer Name des abgelaufenen Timers oder Bereichs
 * @property timeoutMillis konfigurierte Timeout-Dauer in Millisekunden
 */
data class TimeoutTriggered(
    override val lobbyCode: LobbyCode,
    val target: String,
    val timeoutMillis: Long,
) : InternalLobbyEvent {
    init {
        require(target.isNotBlank()) { "TimeoutTriggered.target darf nicht leer sein." }
        require(timeoutMillis > 0) {
            "TimeoutTriggered.timeoutMillis muss positiv sein, war aber $timeoutMillis."
        }
    }
}
