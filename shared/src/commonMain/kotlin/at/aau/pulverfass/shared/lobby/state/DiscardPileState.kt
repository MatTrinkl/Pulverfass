package at.aau.pulverfass.shared.lobby.state

import kotlinx.serialization.Serializable

/**
 * Serverseitiger Ablagestapel für eingetauschte Spielkarten.
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
            "DiscardPileState.withCardsAdded benötigt mindestens eine Karte."
        }

        return copy(cards = cards + addedCards)
    }
}
