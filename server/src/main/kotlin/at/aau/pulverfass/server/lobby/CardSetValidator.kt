package at.aau.pulverfass.server.lobby

import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType

/**
 * Backward-compatible Alias auf den Shared-Validator für Server-Call-Sites und Tests.
 */
object CardSetValidator {
    fun isValidSet(types: List<CardType>): Boolean =
        at.aau.pulverfass.shared.lobby.state.CardSetValidator.isValidSet(types)

    fun canMakeAnySet(cards: List<CardState>): Boolean =
        at.aau.pulverfass.shared.lobby.state.CardSetValidator.canMakeAnySet(cards)
}
