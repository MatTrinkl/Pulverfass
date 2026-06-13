package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene Turn-Advance-Anfragen.
 */
@Serializable
enum class TurnAdvanceErrorCode {
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    GAME_FINISHED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
    FORCED_TRADE_REQUIRED,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Turn-Advance-Anfrage.
 */
@Serializable
data class TurnAdvanceErrorResponse(
    val code: TurnAdvanceErrorCode,
    val reason: String,
) : NetworkMessagePayload

object TurnAdvanceErrorResponseSerializer :
    KSerializer<TurnAdvanceErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TurnAdvanceErrorResponse.serializer(),
    )
