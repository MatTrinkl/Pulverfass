package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Fehlantwort des Servers auf einen nicht erfolgreichen Lobby-Join.
 *
 * @property reason Anzeigegrund für den Client
 */
@Serializable
data class JoinLobbyErrorResponse(
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [JoinLobbyErrorResponse].
 */
object JoinLobbyErrorResponseSerializer :
    KSerializer<JoinLobbyErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        JoinLobbyErrorResponse.serializer(),
    )
