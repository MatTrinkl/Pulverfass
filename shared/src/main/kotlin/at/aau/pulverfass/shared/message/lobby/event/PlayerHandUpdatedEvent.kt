package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.GameState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Privates Transport-Event mit der aktuell autoritativen Hand eines Spielers.
 *
 * Das Event wird gezielt nur an [recipientPlayerId] zugestellt und ist deshalb kein Broadcast
 * an alle Lobby-Teilnehmer.
 *
 * @property lobbyCode betroffene Lobby
 * @property recipientPlayerId Spieler, dessen Hand übertragen wird
 * @property stateVersion State-Version, zu der die Hand gehört
 * @property handCards private Karten-Snapshots des Empfängers
 */
@Serializable
data class PlayerHandUpdatedEvent(
    val lobbyCode: LobbyCode,
    override val recipientPlayerId: PlayerId,
    val stateVersion: Long,
    val handCards: List<PrivateHandCardSnapshot>,
) : PrivateGameEvent {
    companion object {
        /**
         * Baut das private Handevent aus einem vollständigen [GameState].
         *
         * @throws IllegalArgumentException wenn [recipientPlayerId] nicht Teil der Lobby ist
         */
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

/**
 * Serialisierbarer Auszug einer einzelnen Handkarte.
 *
 * @property cardId eindeutige Karten-ID
 * @property type fachlicher Kartentyp
 */
@Serializable
data class PrivateHandCardSnapshot(
    val cardId: CardId,
    val type: CardType,
) {
    companion object {
        /**
         * Erzeugt den Transport-Snapshot aus einer Domain-Karte.
         */
        fun from(card: CardState): PrivateHandCardSnapshot =
            PrivateHandCardSnapshot(cardId = card.cardId, type = card.type)
    }
}

/**
 * Legacy-Serializer für [PlayerHandUpdatedEvent].
 */
object PlayerHandUpdatedEventSerializer :
    KSerializer<PlayerHandUpdatedEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PlayerHandUpdatedEvent.serializer(),
    )

/**
 * Legacy-Serializer für [PrivateHandCardSnapshot].
 */
object PrivateHandCardSnapshotSerializer :
    KSerializer<PrivateHandCardSnapshot> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PrivateHandCardSnapshot.serializer(),
    )
