package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene Bestätigungen des Angriffsphasen-Endes.
 */
@Serializable
enum class ConfirmAttackDoneErrorCode {
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Bestätigung des Angriffsphasen-Endes.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable
data class ConfirmAttackDoneErrorResponse(
    val code: ConfirmAttackDoneErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [ConfirmAttackDoneErrorResponse].
 */
object ConfirmAttackDoneErrorResponseSerializer :
    KSerializer<ConfirmAttackDoneErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ConfirmAttackDoneErrorResponse.serializer(),
    )
