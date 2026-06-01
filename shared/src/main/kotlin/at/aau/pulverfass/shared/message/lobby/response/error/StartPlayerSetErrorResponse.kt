package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
enum class StartPlayerSetErrorCode {
    GAME_NOT_FOUND,
    NOT_HOST,
    PLAYER_NOT_IN_LOBBY,
    GAME_ALREADY_STARTED,
    REQUESTER_MISMATCH,
}

@Serializable
data class StartPlayerSetErrorResponse(
    val code: StartPlayerSetErrorCode,
    val reason: String,
) : NetworkMessagePayload

object StartPlayerSetErrorResponseSerializer :
    KSerializer<StartPlayerSetErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        StartPlayerSetErrorResponse.serializer(),
    )
