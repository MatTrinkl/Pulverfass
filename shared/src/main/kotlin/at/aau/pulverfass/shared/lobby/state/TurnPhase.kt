package at.aau.pulverfass.shared.lobby.state

import kotlinx.serialization.Serializable

/**
 * Feste Reihenfolge der serverseitigen Turn-Phasen.
 */
@Serializable
enum class TurnPhase {
    REINFORCEMENTS,
    ATTACK,
    FORTIFY,
    DRAW_CARD,
    ;

    /**
     * Liefert die deterministisch folgende Phase innerhalb desselben Spielerzugs.
     */
    fun next(): TurnPhase =
        when (this) {
            REINFORCEMENTS -> ATTACK
            ATTACK -> FORTIFY
            FORTIFY -> DRAW_CARD
            DRAW_CARD -> REINFORCEMENTS
        }
}
