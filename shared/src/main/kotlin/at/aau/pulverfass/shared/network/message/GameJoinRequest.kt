package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients, einer bestehenden Lobby beizutreten.
 *
 * @property lobbyCode Ziel-Lobby der Join-Anfrage
 */
@Serializable
data class GameJoinRequest(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload
