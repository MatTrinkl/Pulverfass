package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Zieht autoritativ die oberste Karte des Decks in die Hand eines Spielers.
 */
data class CardDrawnEvent(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val cardId: CardId,
) : InternalLobbyEvent
