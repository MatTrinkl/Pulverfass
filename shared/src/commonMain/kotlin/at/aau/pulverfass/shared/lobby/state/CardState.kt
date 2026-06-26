package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.CardId
import kotlinx.serialization.Serializable

/**
 * Autoritative Laufzeitrepräsentation einer einzelnen Spielkarte.
 */
@Serializable
data class CardState(
    val cardId: CardId,
    val type: CardType,
)
