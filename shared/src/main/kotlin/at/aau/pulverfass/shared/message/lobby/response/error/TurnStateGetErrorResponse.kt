package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene Turn-State-Snapshot-Anfragen.
 */
@Serializable
enum class TurnStateGetErrorCode {
    GAME_NOT_FOUND,
    TURN_STATE_NOT_READY,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Turn-State-Snapshot-Anfrage.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable
data class TurnStateGetErrorResponse(
    val code: TurnStateGetErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [TurnStateGetErrorResponse].
 */
object TurnStateGetErrorResponseSerializer :
    KSerializer<TurnStateGetErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TurnStateGetErrorResponse.serializer(),
    )
