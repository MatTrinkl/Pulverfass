package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.PlayerId
import kotlinx.serialization.Serializable

/**
 * Verbleibender Verstärkungspool eines Spielers für die Reinforcement-Phase.
 */
@Serializable
data class PendingReinforcements(
    val playerId: PlayerId,
    val amount: Int,
) {
    init {
        require(amount >= 0) {
            "PendingReinforcements.amount darf nicht negativ sein, war aber $amount."
        }
    }
}
