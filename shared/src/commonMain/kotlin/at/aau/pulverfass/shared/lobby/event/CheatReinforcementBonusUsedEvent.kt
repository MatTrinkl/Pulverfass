package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Hält fest, dass ein Spieler seinen einmaligen Schummel-Verstärkungsbonus genutzt hat.
 *
 * Das Event enthält absichtlich nur die Lobby und den Spieler. Die zusätzlichen
 * Truppen werden über ein separates PendingReinforcements-Event vergeben. So
 * bleibt im Eventlog klar unterscheidbar, was passiert ist:
 * 1. Der Spieler hat den Cheatbonus verbraucht.
 * 2. Der Verstärkungspool wurde um den Bonuswert erhöht.
 */
data class CheatReinforcementBonusUsedEvent(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
) : InternalLobbyEvent
