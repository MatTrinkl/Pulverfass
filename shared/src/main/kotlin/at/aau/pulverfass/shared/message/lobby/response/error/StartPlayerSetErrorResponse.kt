package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene Startspieler-Konfigurationen.
 */
@Serializable
enum class StartPlayerSetErrorCode {
    GAME_NOT_FOUND,
    NOT_HOST,
    PLAYER_NOT_IN_LOBBY,
    GAME_ALREADY_STARTED,
    REQUESTER_MISMATCH,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Startspieler-Konfiguration.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable
data class StartPlayerSetErrorResponse(
    val code: StartPlayerSetErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [StartPlayerSetErrorResponse].
 */
object StartPlayerSetErrorResponseSerializer :
    KSerializer<StartPlayerSetErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        StartPlayerSetErrorResponse.serializer(),
    )
