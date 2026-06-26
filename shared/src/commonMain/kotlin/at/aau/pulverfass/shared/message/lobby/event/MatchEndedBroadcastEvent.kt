package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.MatchEndReason
import kotlinx.serialization.Serializable

/**
 * Öffentlicher Broadcast für das fachliche Ende eines Matches.
 *
 * @property lobbyCode betroffene Lobby
 * @property reason fachlicher Grund für das Match-Ende
 * @property winnerPlayerId Gewinner des Matches, falls eindeutig bestimmbar
 * @property stateVersion autoritative Version nach Anwendung des Match-Endes
 */
@Serializable
data class MatchEndedBroadcastEvent(
    val lobbyCode: LobbyCode,
    val reason: MatchEndReason,
    val winnerPlayerId: PlayerId? = null,
    val stateVersion: Long? = null,
) : PublicGameEvent
