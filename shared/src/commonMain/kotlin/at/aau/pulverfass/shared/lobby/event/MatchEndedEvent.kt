package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Beendet ein laufendes Match fachlich.
 *
 * @property lobbyCode betroffene Lobby
 * @property reason fachlicher Grund für das Match-Ende
 * @property winnerPlayerId Gewinner des Matches, falls eindeutig bestimmbar
 */
data class MatchEndedEvent(
    override val lobbyCode: LobbyCode,
    val reason: MatchEndReason,
    val winnerPlayerId: PlayerId? = null,
) : InternalLobbyEvent

/**
 * Fachlicher Grund für ein beendetes Match.
 */
enum class MatchEndReason {
    DECK_EMPTY,
    TERRITORY_DOMINATION,
}
