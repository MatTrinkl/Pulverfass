package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HandStateTest {
    @Test
    fun `should add and remove cards from a player hand`() {
        val playerId = PlayerId(1)
        val alphaCard = CardState(cardId = CardId("card-alpha"), type = CardType.A)
        val jokerCard = CardState(cardId = CardId("card-joker"), type = CardType.JOKER)

        val withCards =
            HandState()
                .withCardAdded(playerId, alphaCard)
                .withCardAdded(playerId, jokerCard)
        val withoutAlpha = withCards.withoutCard(playerId, alphaCard.cardId)

        assertEquals(listOf(alphaCard, jokerCard), withCards.cardsOf(playerId))
        assertEquals(2, withCards.handSizeOf(playerId))
        assertTrue(withCards.contains(playerId, jokerCard.cardId))
        assertEquals(listOf(jokerCard), withoutAlpha.cardsOf(playerId))
        assertEquals(1, withoutAlpha.handSizeOf(playerId))
        assertFalse(withoutAlpha.contains(playerId, alphaCard.cardId))
    }

    @Test
    fun `should reject duplicate card ids across all player hands`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val duplicatedCard = CardState(cardId = CardId("card-1"), type = CardType.B)

        assertThrows(IllegalArgumentException::class.java) {
            HandState(
                cardsByPlayer =
                    mapOf(
                        playerOne to listOf(duplicatedCard),
                        playerTwo to listOf(duplicatedCard),
                    ),
            )
        }
    }
}
