package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.message.lobby.PublicGameStateWireSnapshot
import at.aau.pulverfass.shared.message.lobby.event.PublicGameStatePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Autoritative Catch-up-Antwort mit vollständigem öffentlichem GameState-Snapshot.
 *
 * Die Struktur entspricht absichtlich dem Full-Snapshot-Broadcast beim Turnwechsel,
 * wird hier aber als direkte S2C-Antwort auf eine explizite Catch-up-Anfrage genutzt.
 */
@Serializable(with = GameStateCatchUpResponseSerializer::class)
data class GameStateCatchUpResponse(
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val determinism: PublicDeterminismMetadataSnapshot,
    val turnState: PublicTurnStateSnapshot,
    val definition: MapDefinitionSnapshot,
    val territoryStates: List<MapTerritoryStateSnapshot>,
    val gameStatus: GameStatus = GameStatus.RUNNING,
    val matchEndReason: String? = null,
    val winnerPlayerId: PlayerId? = null,
) : PublicGameStatePayload {
    companion object {
        fun from(snapshot: PublicGameStateSnapshot): GameStateCatchUpResponse =
            GameStateCatchUpResponse(
                lobbyCode = snapshot.lobbyCode,
                stateVersion = snapshot.stateVersion,
                determinism = snapshot.determinism,
                turnState = snapshot.turnState,
                definition = snapshot.definition,
                territoryStates = snapshot.territoryStates,
                gameStatus = snapshot.gameStatus,
                matchEndReason = snapshot.matchEndReason,
                winnerPlayerId = snapshot.winnerPlayerId,
            )
    }
}

/**
 * Technischer Serializer fuer [GameStateCatchUpResponse].
 */
object GameStateCatchUpResponseSerializer :
    KSerializer<GameStateCatchUpResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyTransformingSerializer(
        PublicGameStateWireSnapshot.serializer(),
        toWire = { value ->
            PublicGameStateWireSnapshot(
                lobbyCode = value.lobbyCode,
                stateVersion = value.stateVersion,
                determinism = value.determinism,
                turnState = value.turnState,
                definition = value.definition,
                territoryStates = value.territoryStates,
                gameStatus = value.gameStatus,
                matchEndReason = value.matchEndReason,
                winnerPlayerId = value.winnerPlayerId,
            )
        },
        fromWire = { wire ->
            GameStateCatchUpResponse(
                lobbyCode = wire.lobbyCode,
                stateVersion = wire.stateVersion,
                determinism = wire.determinism,
                turnState = wire.turnState,
                definition = wire.definition,
                territoryStates = wire.territoryStates,
                gameStatus = wire.gameStatus,
                matchEndReason = wire.matchEndReason,
                winnerPlayerId = wire.winnerPlayerId,
            )
        },
    )
