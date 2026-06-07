package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.map.config.MapDefinition
import kotlin.random.Random

private const val CARD_DECK_RANDOM_SALT = 0x5DEECE66DL

/**
 * Erzeugt den deterministisch gemischten Kartenstapel für ein neues Spiel.
 *
 * Für jedes Territorium werden die in der Deck-Konfiguration definierten
 * Symbolkarten angelegt. Joker ergänzen den Kartenstapel global.
 */
internal object CardDeckFactory {
    fun createShuffledDeck(
        mapDefinition: MapDefinition,
        randomSeed: Long,
        config: CardDeckConfig = CardDeckConfig.DEFAULT,
    ): DeckState {
        val territoryCards =
            mapDefinition.territories.flatMap { territory ->
                config.symbolsPerTerritory.map { type ->
                    CardState(
                        cardId =
                            CardId(
                                "territory:${territory.territoryId.value}:${type.cardIdSuffix()}",
                            ),
                        type = type,
                    )
                }
            }
        val jokerCards =
            (1..config.jokerCount).map { index ->
                CardState(CardId("joker:$index"), CardType.JOKER)
            }
        val cards = territoryCards + jokerCards
        require(cards.isNotEmpty()) {
            "CardDeckConfig darf keinen leeren Kartenstapel erzeugen."
        }

        return DeckState(
            cards =
                cards
                    .shuffled(Random(randomSeed xor CARD_DECK_RANDOM_SALT)),
        )
    }

    private fun CardType.cardIdSuffix(): String = name.lowercase()
}
