package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort des Servers auf eine Join-Anfrage.
 *
 * @property lobbyCode Lobby, der der Client beigetreten ist
 */
@Serializable
data class JoinLobbyResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [JoinLobbyResponse].
 */
object JoinLobbyResponseSerializer :
    KSerializer<JoinLobbyResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(JoinLobbyResponse.serializer())
