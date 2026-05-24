package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Entfernt autoritativ Karten aus der Hand eines Spielers.
 */
data class PlayerCardsRemovedEvent(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val cardIds: List<CardId>,
) : InternalLobbyEvent {
    init {
        require(cardIds.isNotEmpty()) {
            "PlayerCardsRemovedEvent.cardIds darf nicht leer sein."
        }
        require(cardIds.distinct().size == cardIds.size) {
            "PlayerCardsRemovedEvent.cardIds darf keine Duplikate enthalten."
        }
    }
}
