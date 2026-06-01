package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class TradeInCardsRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val cardIds: List<CardId>,
) : NetworkMessagePayload {
    init {
        require(cardIds.size == 3) {
            "TradeInCardsRequest.cardIds muss genau 3 Karten enthalten."
        }
        require(cardIds.distinct().size == cardIds.size) {
            "TradeInCardsRequest.cardIds darf keine Duplikate enthalten."
        }
    }
}

object TradeInCardsRequestSerializer :
    KSerializer<TradeInCardsRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TradeInCardsRequest.serializer(),
    )
