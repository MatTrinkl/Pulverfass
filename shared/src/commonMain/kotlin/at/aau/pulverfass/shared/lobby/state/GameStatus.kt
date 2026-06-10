package at.aau.pulverfass.shared.lobby.state

/**
 * Minimaler Lifecycle-Status einer Lobby bzw. eines Spiels.
 *
 * Die Statuswerte bilden bewusst nur grobe Phasen ab und enthalten noch keine
 * vollständigen Risiko-Regeln.
 */
enum class GameStatus {
    /**
     * Die Lobby ist angelegt, wartet aber noch auf genug Spieler für einen Lauf.
     */
    WAITING_FOR_PLAYERS,

    /**
     * Das Spiel ist aktiv und verarbeitet Züge.
     */
    RUNNING,

    /**
     * Das Spiel ist fachlich beendet.
     */
    FINISHED,

    /**
     * Die Lobby wurde technisch oder fachlich geschlossen.
     */
    CLOSED,
}
