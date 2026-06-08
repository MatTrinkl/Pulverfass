package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Antwort des Servers auf eine erfolgreiche Lobby-Erstellung.
 *
 * @property lobbyCode serverseitig vergebener Join-Code der neuen Lobby
 */
@Serializable
data class CreateLobbyResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [CreateLobbyResponse].
 */
object CreateLobbyResponseSerializer :
    KSerializer<CreateLobbyResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        CreateLobbyResponse.serializer(),
    )
