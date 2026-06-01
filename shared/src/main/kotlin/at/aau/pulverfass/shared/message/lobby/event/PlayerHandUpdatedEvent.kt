package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.GameState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class PlayerHandUpdatedEvent(
    val lobbyCode: LobbyCode,
    override val recipientPlayerId: PlayerId,
    val stateVersion: Long,
    val handCards: List<PrivateHandCardSnapshot>,
) : PrivateGameEvent {
    companion object {
        fun fromGameState(
            gameState: GameState,
            recipientPlayerId: PlayerId,
        ): PlayerHandUpdatedEvent {
            require(gameState.hasPlayer(recipientPlayerId)) {
                "Spieler '${recipientPlayerId.value}' ist nicht Teil der Lobby " +
                    "'${gameState.lobbyCode.value}'."
            }

            return PlayerHandUpdatedEvent(
                lobbyCode = gameState.lobbyCode,
                recipientPlayerId = recipientPlayerId,
                stateVersion = gameState.stateVersion,
                handCards = gameState.handOf(recipientPlayerId).map(PrivateHandCardSnapshot::from),
            )
        }
    }
}

@Serializable
data class PrivateHandCardSnapshot(
    val cardId: CardId,
    val type: CardType,
) {
    companion object {
        fun from(card: CardState): PrivateHandCardSnapshot =
            PrivateHandCardSnapshot(cardId = card.cardId, type = card.type)
    }
}

object PlayerHandUpdatedEventSerializer :
    KSerializer<PlayerHandUpdatedEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PlayerHandUpdatedEvent.serializer(),
    )

object PrivateHandCardSnapshotSerializer :
    KSerializer<PrivateHandCardSnapshot> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PrivateHandCardSnapshot.serializer(),
    )
