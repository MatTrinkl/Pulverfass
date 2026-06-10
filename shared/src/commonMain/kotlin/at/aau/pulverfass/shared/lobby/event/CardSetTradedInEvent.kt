package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Repräsentiert den erfolgreichen Trade-In eines Dreier-Sets durch einen Spieler.
 */
data class CardSetTradedInEvent(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val cardIds: List<CardId>,
    val value: Int,
    val tradeIndex: Int,
) : InternalLobbyEvent {
    init {
        require(cardIds.size == 3) {
            "CardSetTradedInEvent.cardIds muss genau 3 Karten enthalten."
        }
        require(cardIds.distinct().size == cardIds.size) {
            "CardSetTradedInEvent.cardIds darf keine Duplikate enthalten."
        }
        require(value > 0) {
            "CardSetTradedInEvent.value muss positiv sein, war aber $value."
        }
        require(tradeIndex > 0) {
            "CardSetTradedInEvent.tradeIndex muss positiv sein, war aber $tradeIndex."
        }
    }
}
