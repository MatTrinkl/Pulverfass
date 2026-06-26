package at.aau.pulverfass.shared.lobby.state

import kotlinx.serialization.Serializable

/**
 * Konfiguration für den Kartenstapel eines Spiels.
 */
@Serializable
data class CardDeckConfig(
    val symbolsPerTerritory: List<CardType> = DEFAULT_SYMBOLS_PER_TERRITORY,
    val jokerCount: Int = DEFAULT_JOKER_COUNT,
) {
    init {
        require(symbolsPerTerritory.isNotEmpty()) {
            "CardDeckConfig.symbolsPerTerritory darf nicht leer sein."
        }
        require(symbolsPerTerritory.none { type -> type == CardType.JOKER }) {
            "CardDeckConfig.symbolsPerTerritory darf keine Joker enthalten."
        }
        require(symbolsPerTerritory.distinct().size == symbolsPerTerritory.size) {
            "CardDeckConfig.symbolsPerTerritory darf keine Duplikate enthalten."
        }
        require(jokerCount >= 0) {
            "CardDeckConfig.jokerCount darf nicht negativ sein, war aber $jokerCount."
        }
    }

    companion object {
        val DEFAULT_SYMBOLS_PER_TERRITORY = listOf(CardType.A, CardType.B, CardType.C)
        const val DEFAULT_JOKER_COUNT = 4
        val DEFAULT = CardDeckConfig()
    }
}
