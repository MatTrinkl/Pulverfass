package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.CardId
import kotlinx.serialization.Serializable

/**
 * Platzhalter für den serverseitigen Kartenstapel.
 */
@Serializable
data class DeckState(
    val cards: List<CardState> = emptyList(),
) {
    init {
        validateDistinctCardIds(cards, "DeckState")
    }

    fun topCard(): CardState? = cards.firstOrNull()

    internal fun withoutTopCard(cardId: CardId): DeckState {
        require(cards.firstOrNull()?.cardId == cardId) {
            "Card '${cardId.value}' ist nicht die oberste Karte des Decks."
        }

        return copy(cards = cards.drop(1))
    }
}
