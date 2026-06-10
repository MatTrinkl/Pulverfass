package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients, den aktuellen autoritativen Turn-State einer Lobby
 * als Snapshot vom Server abzurufen.
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable
data class TurnStateGetRequest(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [TurnStateGetRequest].
 */
object TurnStateGetRequestSerializer :
    KSerializer<TurnStateGetRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TurnStateGetRequest.serializer(),
    )
