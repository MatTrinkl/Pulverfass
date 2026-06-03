package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes fuer fehlgeschlagene Catch-up-Snapshot-Anfragen.
 */
@Serializable
enum class GameStateCatchUpErrorCode {
    GAME_NOT_FOUND,
    NOT_IN_GAME,
    SNAPSHOT_NOT_READY,
    PAYLOAD_TOO_LARGE,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Catch-up-Anfrage.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable
data class GameStateCatchUpErrorResponse(
    val code: GameStateCatchUpErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer fuer [GameStateCatchUpErrorResponse].
 */
object GameStateCatchUpErrorResponseSerializer :
    KSerializer<GameStateCatchUpErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        GameStateCatchUpErrorResponse.serializer(),
    )
