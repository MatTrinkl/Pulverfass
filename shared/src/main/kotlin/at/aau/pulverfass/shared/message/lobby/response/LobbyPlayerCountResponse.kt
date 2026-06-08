package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort des Servers mit der aktuellen Anzahl an Spielern in einer Lobby.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerCount aktuell autoritativ ermittelte Spieleranzahl
 */
@Serializable
data class LobbyPlayerCountResponse(
    val lobbyCode: LobbyCode,
    val playerCount: Int,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [LobbyPlayerCountResponse].
 */
object LobbyPlayerCountResponseSerializer :
    KSerializer<LobbyPlayerCountResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        LobbyPlayerCountResponse.serializer(),
    )
