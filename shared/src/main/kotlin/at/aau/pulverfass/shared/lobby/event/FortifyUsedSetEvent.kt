package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

data class FortifyUsedSetEvent(
    override val lobbyCode: LobbyCode,
    val used: Boolean,
) : InternalLobbyEvent
