package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene Fortify-Anfragen.
 */
@Serializable
enum class FortifyMoveErrorCode {
    GAME_NOT_FOUND,
    REQUESTER_MISMATCH,
    GAME_PAUSED,
    NOT_ACTIVE_PLAYER,
    WRONG_PHASE,
    TERRITORY_NOT_OWNED,
    NO_PATH,
    INSUFFICIENT_TROOPS,
    FORTIFY_ALREADY_USED,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Fortify-Anfrage.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable
data class FortifyMoveErrorResponse(
    val code: FortifyMoveErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [FortifyMoveErrorResponse].
 */
object FortifyMoveErrorResponseSerializer :
    KSerializer<FortifyMoveErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        FortifyMoveErrorResponse.serializer(),
    )
