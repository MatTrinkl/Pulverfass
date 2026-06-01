package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.PrivateGameStateWireSnapshot
import at.aau.pulverfass.shared.message.lobby.event.PrivateGameStatePayload
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Privater Snapshot für genau einen Spieler.
 *
 * Die fachlichen privaten Sammlungen sind aktuell noch Platzhalter für spätere
 * Systeme wie Handkarten oder geheime Ziele. Der Transportpfad ist damit bereits
 * vorhanden, ohne heute schon private Daten zu leaken.
 *
 * @property lobbyCode betroffene Lobby
 * @property recipientPlayerId autorisierter Empfänger dieses Snapshots
 * @property stateVersion öffentliche State-Version, zu der dieser private Snapshot passt
 * @property handCards private Handkarten des Spielers
 * @property secretObjectives private Missions- oder Zielinformationen des Spielers
 * @property privateHandCards typisierte Handkarten inklusive der für Trade-ins nötigen IDs
 */
@Serializable(with = GameStatePrivateGetResponseSerializer::class)
data class GameStatePrivateGetResponse(
    val lobbyCode: LobbyCode,
    override val recipientPlayerId: PlayerId,
    val stateVersion: Long,
    val handCards: List<String> = emptyList(),
    val secretObjectives: List<String> = emptyList(),
    val privateHandCards: List<PrivateHandCardSnapshot> = emptyList(),
) : PrivateGameStatePayload {
    companion object {
        fun fromGameState(
            gameState: GameState,
            recipientPlayerId: PlayerId,
        ): GameStatePrivateGetResponse = gameState.toGameStatePrivateGetResponse(recipientPlayerId)
    }
}

/**
 * Technischer Serializer für [GameStatePrivateGetResponse].
 */
object GameStatePrivateGetResponseSerializer :
    KSerializer<GameStatePrivateGetResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyTransformingSerializer(
        PrivateGameStateWireSnapshot.serializer(),
        toWire = { value ->
            PrivateGameStateWireSnapshot(
                lobbyCode = value.lobbyCode,
                recipientPlayerId = value.recipientPlayerId,
                stateVersion = value.stateVersion,
                handCards = value.handCards,
                secretObjectives = value.secretObjectives,
                privateHandCards = value.privateHandCards,
            )
        },
        fromWire = { wire ->
            GameStatePrivateGetResponse(
                lobbyCode = wire.lobbyCode,
                recipientPlayerId = wire.recipientPlayerId,
                stateVersion = wire.stateVersion,
                handCards = wire.handCards,
                secretObjectives = wire.secretObjectives,
                privateHandCards = wire.privateHandCards,
            )
        },
    )

internal fun GameState.toGameStatePrivateGetResponse(
    recipientPlayerId: PlayerId,
): GameStatePrivateGetResponse {
    require(hasPlayer(recipientPlayerId)) {
        "Spieler '${recipientPlayerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
    }

    return GameStatePrivateGetResponse(
        lobbyCode = lobbyCode,
        recipientPlayerId = recipientPlayerId,
        stateVersion = stateVersion,
        handCards = emptyList(),
        secretObjectives = emptyList(),
        privateHandCards = handOf(recipientPlayerId).map(PrivateHandCardSnapshot::from),
    )
}
