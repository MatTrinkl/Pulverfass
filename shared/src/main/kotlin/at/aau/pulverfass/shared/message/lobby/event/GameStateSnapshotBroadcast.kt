package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.PublicGameStateWireSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicGameStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Vollständiger öffentlicher GameState-Snapshot für Self-Healing beim Turnwechsel.
 *
 * Der Broadcast enthält ausschließlich öffentliche Informationen und soll Clients
 * nach einem Spielerwechsel auf einen konsistenten Stand zurückführen.
 */
@Serializable(with = GameStateSnapshotBroadcastSerializer::class)
data class GameStateSnapshotBroadcast(
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val determinism: PublicDeterminismMetadataSnapshot,
    val turnState: PublicTurnStateSnapshot,
    val definition: MapDefinitionSnapshot,
    val territoryStates: List<MapTerritoryStateSnapshot>,
) : PublicGameStatePayload {
    companion object {
        fun from(snapshot: PublicGameStateSnapshot): GameStateSnapshotBroadcast =
            GameStateSnapshotBroadcast(
                lobbyCode = snapshot.lobbyCode,
                stateVersion = snapshot.stateVersion,
                determinism = snapshot.determinism,
                turnState = snapshot.turnState,
                definition = snapshot.definition,
                territoryStates = snapshot.territoryStates,
            )
    }
}

/**
 * Technischer Serializer für [GameStateSnapshotBroadcast].
 */
object GameStateSnapshotBroadcastSerializer :
    KSerializer<GameStateSnapshotBroadcast> by
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
            )
        },
        fromWire = { wire ->
            GameStateSnapshotBroadcast(
                lobbyCode = wire.lobbyCode,
                stateVersion = wire.stateVersion,
                determinism = wire.determinism,
                turnState = wire.turnState,
                definition = wire.definition,
                territoryStates = wire.territoryStates,
            )
        },
    )
