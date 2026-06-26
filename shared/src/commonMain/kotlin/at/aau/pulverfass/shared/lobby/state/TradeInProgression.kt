package at.aau.pulverfass.shared.lobby.state

/**
 * Deterministische Progression der Verstärkungswerte für global eingetauschte Kartensets.
 */
object TradeInProgression {
    fun tradeInValue(tradeIndex1Based: Int): Int =
        when (tradeIndex1Based) {
            1 -> 2
            2 -> 4
            3 -> 6
            4 -> 8
            else -> {
                require(tradeIndex1Based >= 5) {
                    "tradeIndex1Based muss positiv sein, war aber $tradeIndex1Based."
                }
                10
            }
        }
}
