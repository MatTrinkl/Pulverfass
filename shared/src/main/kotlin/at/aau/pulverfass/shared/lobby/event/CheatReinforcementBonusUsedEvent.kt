package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Hält fest, dass ein Spieler seinen einmaligen Schummel-Verstärkungsbonus genutzt hat.
 */
data class CheatReinforcementBonusUsedEvent(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
) : InternalLobbyEvent
