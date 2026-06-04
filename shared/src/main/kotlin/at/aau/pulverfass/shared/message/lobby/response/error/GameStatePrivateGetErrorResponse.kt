package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene private GameState-Snapshot-Anfragen.
 */
@Serializable
enum class GameStatePrivateGetErrorCode {
    GAME_NOT_FOUND,
    NOT_IN_GAME,
    REQUESTER_MISMATCH,
    PAYLOAD_TOO_LARGE,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche private Snapshot-Anfrage.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable
data class GameStatePrivateGetErrorResponse(
    val code: GameStatePrivateGetErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [GameStatePrivateGetErrorResponse].
 */
object GameStatePrivateGetErrorResponseSerializer :
    KSerializer<GameStatePrivateGetErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        GameStatePrivateGetErrorResponse.serializer(),
    )
