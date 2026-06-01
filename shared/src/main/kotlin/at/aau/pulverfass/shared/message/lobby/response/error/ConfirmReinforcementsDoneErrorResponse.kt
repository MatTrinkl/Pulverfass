package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
enum class ConfirmReinforcementsDoneErrorCode {
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
    PENDING_REINFORCEMENTS_REMAINING,
    FORCED_TRADE_REQUIRED,
}

@Serializable
data class ConfirmReinforcementsDoneErrorResponse(
    val code: ConfirmReinforcementsDoneErrorCode,
    val reason: String,
) : NetworkMessagePayload

object ConfirmReinforcementsDoneErrorResponseSerializer :
    KSerializer<ConfirmReinforcementsDoneErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ConfirmReinforcementsDoneErrorResponse.serializer(),
    )
