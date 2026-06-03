package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene Verstärkungsplatzierungen.
 */
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

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Verstärkungsplatzierung.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable
data class PlaceReinforcementsErrorResponse(
    val code: PlaceReinforcementsErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [PlaceReinforcementsErrorResponse].
 */
object PlaceReinforcementsErrorResponseSerializer :
    KSerializer<PlaceReinforcementsErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PlaceReinforcementsErrorResponse.serializer(),
    )
