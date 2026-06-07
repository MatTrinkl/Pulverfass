package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Beendet ein laufendes Match fachlich.
 */
data class MatchEndedEvent(
    override val lobbyCode: LobbyCode,
    val reason: MatchEndReason,
) : InternalLobbyEvent

enum class MatchEndReason {
    DECK_EMPTY,
}
