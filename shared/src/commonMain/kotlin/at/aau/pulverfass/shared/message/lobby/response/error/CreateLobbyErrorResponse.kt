package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Lobby-Erstellung.
 *
 * @property reason Anzeigegrund für den Client
 */
@Serializable
data class CreateLobbyErrorResponse(
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [CreateLobbyErrorResponse].
 */
object CreateLobbyErrorResponseSerializer :
    KSerializer<CreateLobbyErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        CreateLobbyErrorResponse.serializer(),
    )
