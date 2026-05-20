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

    @Test
    fun `withCardAdded rejects card whose id already exists in another hand`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val card = CardState(cardId = CardId("shared-card"), type = CardType.A)
        val hand = HandState().withCardAdded(playerOne, card)

        assertThrows(IllegalArgumentException::class.java) {
            hand.withCardAdded(playerTwo, card)
        }
    }

    @Test
    fun `withoutCard rejects removal of card not held by player`() {
        val playerId = PlayerId(1)
        val card = CardState(cardId = CardId("present"), type = CardType.B)
        val absentId = CardId("absent")
        val hand = HandState().withCardAdded(playerId, card)

        assertThrows(IllegalArgumentException::class.java) {
            hand.withoutCard(playerId, absentId)
        }
    }

    @Test
    fun `withoutCard removes player entry when last card is taken out`() {
        val playerId = PlayerId(1)
        val card = CardState(cardId = CardId("only-card"), type = CardType.C)
        val hand = HandState().withCardAdded(playerId, card)

        val empty = hand.withoutCard(playerId, card.cardId)

        assertFalse(empty.cardsByPlayer.containsKey(playerId))
        assertEquals(0, empty.handSizeOf(playerId))
    }
}
