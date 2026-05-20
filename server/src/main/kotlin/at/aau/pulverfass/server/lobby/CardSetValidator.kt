package at.aau.pulverfass.server.lobby

import at.aau.pulverfass.shared.lobby.state.CardType

/**
 * Deterministische Validierung von Dreier-Sets für den Karten-Trade-In.
 */
object CardSetValidator {
    private val validTargets =
        listOf(
            listOf(CardType.A, CardType.A, CardType.A),
            listOf(CardType.B, CardType.B, CardType.B),
            listOf(CardType.C, CardType.C, CardType.C),
            listOf(CardType.A, CardType.B, CardType.C),
        )

    fun isValidSet(types: List<CardType>): Boolean {
        if (types.size != 3) {
            return false
        }

        val jokers = types.count { type -> type == CardType.JOKER }
        val nonJokers = types.filterNot { type -> type == CardType.JOKER }

        return validTargets.any { target ->
            val remainingByType = target.groupingBy { type -> type }.eachCount().toMutableMap()

            nonJokers.forEach { type ->
                val remaining = remainingByType[type] ?: 0
                if (remaining == 0) {
                    return@any false
                }
                remainingByType[type] = remaining - 1
            }

            remainingByType.values.sum() == jokers
        }
    }
}
