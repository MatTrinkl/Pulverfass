package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CardDeckFactoryTest {
    @Test
    fun `creates one card of each configured symbol per territory plus jokers`() {
        val deck = CardDeckFactory.createShuffledDeck(sampleMapDefinition(), randomSeed = 42L)

        assertEquals(16, deck.cards.size)
        assertEquals(4, deck.cards.count { card -> card.type == CardType.A })
        assertEquals(4, deck.cards.count { card -> card.type == CardType.B })
        assertEquals(4, deck.cards.count { card -> card.type == CardType.C })
        assertEquals(4, deck.cards.count { card -> card.type == CardType.JOKER })
        assertEquals(deck.cards.size, deck.cards.map { card -> card.cardId }.distinct().size)
        assertEquals(
            setOf(
                "territory:alpha:a",
                "territory:alpha:b",
                "territory:alpha:c",
            ),
            deck.cards
                .filter { card -> card.cardId.value.startsWith("territory:alpha:") }
                .map { card -> card.cardId.value }
                .toSet(),
        )
    }

    @Test
    fun `deck order is deterministic for the same seed`() {
        val firstDeck = CardDeckFactory.createShuffledDeck(sampleMapDefinition(), randomSeed = 7L)
        val secondDeck = CardDeckFactory.createShuffledDeck(sampleMapDefinition(), randomSeed = 7L)
        val differentDeck =
            CardDeckFactory.createShuffledDeck(
                sampleMapDefinition(),
                randomSeed = 8L,
            )

        assertEquals(firstDeck, secondDeck)
        assertEquals(false, firstDeck == differentDeck)
    }

    @Test
    fun `supports explicit deck configuration`() {
        val deck =
            CardDeckFactory.createShuffledDeck(
                sampleMapDefinition(),
                randomSeed = 11L,
                config = CardDeckConfig(symbolsPerTerritory = listOf(CardType.A), jokerCount = 1),
            )

        assertEquals(5, deck.cards.size)
        assertEquals(4, deck.cards.count { card -> card.type == CardType.A })
        assertEquals(1, deck.cards.count { card -> card.type == CardType.JOKER })
    }

    @Test
    fun `rejects invalid deck configuration`() {
        assertThrows(IllegalArgumentException::class.java) {
            CardDeckConfig(symbolsPerTerritory = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CardDeckConfig(symbolsPerTerritory = listOf(CardType.JOKER))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CardDeckConfig(symbolsPerTerritory = listOf(CardType.A, CardType.A))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CardDeckConfig(jokerCount = -1)
        }
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
