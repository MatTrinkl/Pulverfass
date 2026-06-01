package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
enum class TradeInCardsErrorCode {
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
    CARDS_NOT_OWNED,
    INVALID_SET,
    INVALID_REQUEST,
}

@Serializable
data class TradeInCardsErrorResponse(
    val code: TradeInCardsErrorCode,
    val reason: String,
) : NetworkMessagePayload

object TradeInCardsErrorResponseSerializer :
    KSerializer<TradeInCardsErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TradeInCardsErrorResponse.serializer(),
    )
