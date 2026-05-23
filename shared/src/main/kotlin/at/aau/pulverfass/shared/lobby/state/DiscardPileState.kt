package at.aau.pulverfass.shared.lobby.state

import kotlinx.serialization.Serializable

/**
 * Platzhalter für den serverseitigen Ablagestapel.
 */
@Serializable
data class DiscardPileState(
    val cards: List<CardState> = emptyList(),
) {
    init {
        validateDistinctCardIds(cards, "DiscardPileState")
    }
}
