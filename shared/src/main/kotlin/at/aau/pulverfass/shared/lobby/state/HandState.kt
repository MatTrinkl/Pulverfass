package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.PlayerId
import kotlinx.serialization.Serializable

/**
 * Kartenhände aller Spieler.
 */
@Serializable
data class HandState(
    val cardsByPlayer: Map<PlayerId, List<CardState>> = emptyMap(),
) {
    init {
        validateDistinctCardIds(cardsByPlayer.values.flatten(), "HandState")
    }

    fun cardsOf(playerId: PlayerId): List<CardState> = cardsByPlayer[playerId].orEmpty()

    fun contains(
        playerId: PlayerId,
        cardId: CardId,
    ): Boolean {
        return cardsOf(playerId).any { card -> card.cardId == cardId }
    }

    fun handSizeOf(playerId: PlayerId): Int = cardsOf(playerId).size

    internal fun withCardAdded(
        playerId: PlayerId,
        card: CardState,
    ): HandState {
        require(
            cardsByPlayer.values.flatten().none { existing ->
                existing.cardId == card.cardId
            },
        ) {
            "Card '${card.cardId.value}' ist bereits in einer Hand vorhanden."
        }

        return copy(
            cardsByPlayer = cardsByPlayer + (playerId to (cardsOf(playerId) + card)),
        )
    }

    internal fun withoutCard(
        playerId: PlayerId,
        cardId: CardId,
    ): HandState {
        require(contains(playerId, cardId)) {
            "Card '${cardId.value}' ist nicht in der Hand von Spieler '${playerId.value}'."
        }

        val remainingCards = cardsOf(playerId).filterNot { card -> card.cardId == cardId }
        val updatedCardsByPlayer =
            if (remainingCards.isEmpty()) {
                cardsByPlayer - playerId
            } else {
                cardsByPlayer + (playerId to remainingCards)
            }

        return copy(cardsByPlayer = updatedCardsByPlayer)
    }
}
