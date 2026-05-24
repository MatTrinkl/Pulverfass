package at.aau.pulverfass.shared.lobby.state

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
}
