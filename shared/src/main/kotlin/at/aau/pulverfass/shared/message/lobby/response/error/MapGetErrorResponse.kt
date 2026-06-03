package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene Map-Snapshot-Anfragen.
 */
@Serializable
enum class MapGetErrorCode {
    GAME_NOT_FOUND,
    NOT_IN_GAME,
    MAP_NOT_READY,
    PAYLOAD_TOO_LARGE,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Map-Snapshot-Anfrage.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable
data class MapGetErrorResponse(
    val code: MapGetErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [MapGetErrorResponse].
 */
object MapGetErrorResponseSerializer :
    KSerializer<MapGetErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        MapGetErrorResponse.serializer(),
    )
