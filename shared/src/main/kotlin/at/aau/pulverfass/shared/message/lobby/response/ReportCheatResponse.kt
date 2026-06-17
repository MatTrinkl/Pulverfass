package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort auf eine Cheat-Meldung.
 */
@Serializable
data class ReportCheatResponse(
    val lobbyCode: LobbyCode,
    val accusedPlayerId: PlayerId,
    val correct: Boolean,
    val modifierDelta: Int,
) : NetworkMessagePayload
