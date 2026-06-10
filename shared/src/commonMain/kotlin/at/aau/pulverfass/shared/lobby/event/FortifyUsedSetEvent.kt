package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Internes Domain-Event, das markiert, ob die einmalige Fortify-Aktion eines Zuges bereits
 * verbraucht wurde.
 *
 * @property lobbyCode betroffene Lobby
 * @property used `true`, wenn in diesem Zug bereits fortifiziert wurde
 */
data class FortifyUsedSetEvent(
    override val lobbyCode: LobbyCode,
    val used: Boolean,
) : InternalLobbyEvent
