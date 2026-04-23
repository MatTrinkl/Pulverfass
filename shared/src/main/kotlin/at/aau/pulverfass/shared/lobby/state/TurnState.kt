package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Eindeutiger Turn-Zustand einer Lobby.
 *
 * @property activePlayerId aktuell aktiver Spieler
 * @property turnPhase aktuell aktive Phase des Spielers
 * @property turnCount Rundenzähler ab der ersten gestarteten Runde
 * @property startPlayerId Referenzspieler zum Erkennen eines neuen Rundenbeginns
 * @property isPaused signalisiert, dass die Turn-Maschine fachlich pausiert ist
 * @property pauseReason optionale Begründung für den Pause-Zustand
 * @property pausedPlayerId optionaler Spieler, auf dessen Verbindung gewartet wird
 */
data class TurnState(
    val activePlayerId: PlayerId,
    val turnPhase: TurnPhase,
    val turnCount: Int = 1,
    val startPlayerId: PlayerId,
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val pausedPlayerId: PlayerId? = null,
) {
    init {
        require(turnCount >= 1) {
            "TurnState.turnCount darf nicht kleiner als 1 sein, war aber $turnCount."
        }
        require(!isPaused || !pauseReason.isNullOrBlank()) {
            "TurnState.pauseReason muss gesetzt sein, wenn isPaused=true ist."
        }
        require(isPaused || pauseReason == null) {
            "TurnState.pauseReason darf nur gesetzt sein, wenn isPaused=true ist."
        }
        require(isPaused || pausedPlayerId == null) {
            "TurnState.pausedPlayerId darf nur gesetzt sein, wenn isPaused=true ist."
        }
        require(pausedPlayerId == null || pauseReason == TurnPauseReasons.WAITING_FOR_PLAYER) {
            "TurnState.pausedPlayerId darf nur mit PauseReason " +
                "'${TurnPauseReasons.WAITING_FOR_PLAYER}' gesetzt sein."
        }
        require(pauseReason != TurnPauseReasons.WAITING_FOR_PLAYER || pausedPlayerId != null) {
            "TurnState.pausedPlayerId muss gesetzt sein, wenn " +
                "pauseReason='${TurnPauseReasons.WAITING_FOR_PLAYER}' ist."
        }
        require(pausedPlayerId == null || pausedPlayerId == activePlayerId) {
            "TurnState.pausedPlayerId muss dem aktiven Spieler entsprechen."
        }
    }
}
