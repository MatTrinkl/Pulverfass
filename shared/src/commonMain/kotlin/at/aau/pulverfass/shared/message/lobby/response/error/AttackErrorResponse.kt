package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene Angriffsanfragen.
 */
@Serializable
enum class AttackErrorCode {
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
    FROM_TERRITORY_NOT_OWNED,
    ATTACKING_OWN_TERRITORY,
    NOT_ADJACENT,
    INSUFFICIENT_TROOPS,
    INVALID_MOVE_AFTER_CAPTURE,
    INVALID_REQUEST,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Angriffsanfrage.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 * @property requestId optionale Rückgabe der Client-Korrelation aus der Anfrage
 */
@Serializable
data class AttackErrorResponse(
    val code: AttackErrorCode,
    val reason: String,
    val requestId: String? = null,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [AttackErrorResponse].
 */
object AttackErrorResponseSerializer :
    KSerializer<AttackErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        AttackErrorResponse.serializer(),
    )
