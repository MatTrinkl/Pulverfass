package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Spielers, einen vermuteten Cheat eines anderen Spielers zu melden.
 */
@Serializable
data class ReportCheatRequest(
    val lobbyCode: LobbyCode,
    val reporterPlayerId: PlayerId,
    val accusedPlayerId: PlayerId,
) : NetworkMessagePayload
