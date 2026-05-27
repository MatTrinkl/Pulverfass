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

    internal fun withCardsAdded(addedCards: List<CardState>): DiscardPileState {
        require(addedCards.isNotEmpty()) {
            "DiscardPileState.withCardsAdded benoetigt mindestens eine Karte."
        }

        return copy(cards = cards + addedCards)
    }
}
