package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.map.config.MapDefinition
import kotlin.random.Random

private const val CARD_DECK_RANDOM_SALT = 0x5DEECE66DL

/**
 * Erzeugt den deterministisch gemischten Kartenstapel für ein neues Spiel.
 *
 * Für jedes Territorium wird genau eine Symbolkarte angelegt. Zwei Joker
 * ergänzen den Kartenstapel.
 */
internal object CardDeckFactory {
    private val symbolTypes = listOf(CardType.A, CardType.B, CardType.C)

    fun createShuffledDeck(
        mapDefinition: MapDefinition,
        randomSeed: Long,
    ): DeckState {
        val territoryCards =
            mapDefinition.territories.mapIndexed { index, territory ->
                CardState(
                    cardId = CardId("territory:${territory.territoryId.value}"),
                    type = symbolTypes[index % symbolTypes.size],
                )
            }
        val jokerCards =
            listOf(
                CardState(CardId("joker:1"), CardType.JOKER),
                CardState(CardId("joker:2"), CardType.JOKER),
            )

        return DeckState(
            cards =
                (territoryCards + jokerCards)
                    .shuffled(Random(randomSeed xor CARD_DECK_RANDOM_SALT)),
        )
    }
}
