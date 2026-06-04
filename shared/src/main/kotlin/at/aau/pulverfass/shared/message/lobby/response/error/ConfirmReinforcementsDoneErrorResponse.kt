package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene Bestätigungen des Verstärkungsphasen-Endes.
 */
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

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Bestätigung des Verstärkungsphasen-Endes.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable
data class ConfirmReinforcementsDoneErrorResponse(
    val code: ConfirmReinforcementsDoneErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [ConfirmReinforcementsDoneErrorResponse].
 */
object ConfirmReinforcementsDoneErrorResponseSerializer :
    KSerializer<ConfirmReinforcementsDoneErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ConfirmReinforcementsDoneErrorResponse.serializer(),
    )
