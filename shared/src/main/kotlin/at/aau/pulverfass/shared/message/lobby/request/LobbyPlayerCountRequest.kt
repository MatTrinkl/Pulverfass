package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients, den aktuellen autoritativen Spielerstand einer Lobby
 * direkt vom Server abzurufen.
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable
data class LobbyPlayerCountRequest(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [LobbyPlayerCountRequest].
 */
object LobbyPlayerCountRequestSerializer :
    KSerializer<LobbyPlayerCountRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        LobbyPlayerCountRequest.serializer(),
    )
