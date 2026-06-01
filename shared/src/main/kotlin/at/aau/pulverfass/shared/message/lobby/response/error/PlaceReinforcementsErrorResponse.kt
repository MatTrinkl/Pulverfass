package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
enum class PlaceReinforcementsErrorCode {
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
    TERRITORY_NOT_OWNED,
    OVERSPEND,
    INVALID_PLACEMENT,
    FORCED_TRADE_REQUIRED,
}

@Serializable
data class PlaceReinforcementsErrorResponse(
    val code: PlaceReinforcementsErrorCode,
    val reason: String,
) : NetworkMessagePayload

object PlaceReinforcementsErrorResponseSerializer :
    KSerializer<PlaceReinforcementsErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PlaceReinforcementsErrorResponse.serializer(),
    )
