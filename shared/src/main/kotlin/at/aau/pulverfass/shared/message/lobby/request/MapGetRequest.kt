package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients, einen vollständigen Map-Snapshot vom Server abzurufen.
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable
data class MapGetRequest(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [MapGetRequest].
 */
object MapGetRequestSerializer :
    KSerializer<MapGetRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(MapGetRequest.serializer())
