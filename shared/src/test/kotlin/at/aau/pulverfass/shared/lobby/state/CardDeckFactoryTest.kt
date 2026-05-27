package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CardDeckFactoryTest {
    @Test
    fun `creates one territory card per territory plus two jokers`() {
        val deck = CardDeckFactory.createShuffledDeck(sampleMapDefinition(), randomSeed = 42L)

        assertEquals(6, deck.cards.size)
        assertEquals(2, deck.cards.count { card -> card.type == CardType.JOKER })
        assertEquals(
            setOf(CardType.A, CardType.B, CardType.C, CardType.JOKER),
            deck.cards.map { card -> card.type }.toSet(),
        )
        assertEquals(deck.cards.size, deck.cards.map { card -> card.cardId }.distinct().size)
    }

    @Test
    fun `deck order is deterministic for the same seed`() {
        val firstDeck = CardDeckFactory.createShuffledDeck(sampleMapDefinition(), randomSeed = 7L)
        val secondDeck = CardDeckFactory.createShuffledDeck(sampleMapDefinition(), randomSeed = 7L)
        val differentDeck = CardDeckFactory.createShuffledDeck(sampleMapDefinition(), randomSeed = 8L)

        assertEquals(firstDeck, secondDeck)
        assertEquals(false, firstDeck == differentDeck)
    }

    private fun sampleMapDefinition(): MapDefinition =
        MapDefinition(
            schemaVersion = 1,
            territories =
                listOf(
                    TerritoryDefinition(TerritoryId("alpha"), edges = emptyList()),
                    TerritoryDefinition(TerritoryId("beta"), edges = emptyList()),
                    TerritoryDefinition(TerritoryId("gamma"), edges = emptyList()),
                    TerritoryDefinition(TerritoryId("delta"), edges = emptyList()),
                ),
            continents =
                listOf(
                    ContinentDefinition(
                        continentId = ContinentId("all"),
                        territoryIds =
                            listOf(
                                TerritoryId("alpha"),
                                TerritoryId("beta"),
                                TerritoryId("gamma"),
                                TerritoryId("delta"),
                            ),
                        bonusValue = 1,
                    ),
                ),
        )
}
