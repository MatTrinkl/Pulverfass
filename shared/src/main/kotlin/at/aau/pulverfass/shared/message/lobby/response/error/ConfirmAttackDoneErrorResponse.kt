package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
enum class ConfirmAttackDoneErrorCode {
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
}

@Serializable
data class ConfirmAttackDoneErrorResponse(
    val code: ConfirmAttackDoneErrorCode,
    val reason: String,
) : NetworkMessagePayload

object ConfirmAttackDoneErrorResponseSerializer :
    KSerializer<ConfirmAttackDoneErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ConfirmAttackDoneErrorResponse.serializer(),
    )
