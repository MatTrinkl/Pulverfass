package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Fehlantwort des Servers auf eine fehlgeschlagene StartGame-Anfrage.
 *
 * @property reason Anzeigegrund für den Client (z.B. "not_owner", "not_enough_players")
 */
@Serializable
data class StartGameErrorResponse(
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [StartGameErrorResponse].
 */
object StartGameErrorResponseSerializer :
    KSerializer<StartGameErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        StartGameErrorResponse.serializer(),
    )
