package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Fehlantwort des Servers auf eine fehlgeschlagene Kick-Anfrage.
 *
 * @property reason Anzeigegrund für den Client (z.B. "not_owner", "player_not_found")
 */
@Serializable
data class KickPlayerErrorResponse(
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [KickPlayerErrorResponse].
 */
object KickPlayerErrorResponseSerializer :
    KSerializer<KickPlayerErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        KickPlayerErrorResponse.serializer(),
    )
