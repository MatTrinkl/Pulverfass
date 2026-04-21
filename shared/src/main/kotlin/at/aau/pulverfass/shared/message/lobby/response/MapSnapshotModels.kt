package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import kotlinx.serialization.Serializable

/**
 * Serialisierbarer Snapshot der readonly Map-Definition für den Netzwerktrafic.
 */
@Serializable
data class MapDefinitionSnapshot(
    val territories: List<MapTerritoryDefinitionSnapshot>,
    val continents: List<MapContinentDefinitionSnapshot>,
) {
    companion object {
        fun from(definition: MapDefinition): MapDefinitionSnapshot =
            MapDefinitionSnapshot(
                territories = definition.territories.map(MapTerritoryDefinitionSnapshot::from),
                continents = definition.continents.map(MapContinentDefinitionSnapshot::from),
            )
    }
}

/**
 * Serialisierbarer Snapshot eines Territoriums aus der Map-Definition.
 */
@Serializable
data class MapTerritoryDefinitionSnapshot(
    val territoryId: TerritoryId,
    val edges: List<MapTerritoryEdgeSnapshot>,
) {
    companion object {
        fun from(definition: TerritoryDefinition): MapTerritoryDefinitionSnapshot =
            MapTerritoryDefinitionSnapshot(
                territoryId = definition.territoryId,
                edges = definition.edges.map(MapTerritoryEdgeSnapshot::from),
            )
    }
}

/**
 * Serialisierbarer Snapshot einer Kante innerhalb der Map-Definition.
 */
@Serializable
data class MapTerritoryEdgeSnapshot(
    val targetId: TerritoryId,
) {
    companion object {
        fun from(edge: TerritoryEdgeDefinition): MapTerritoryEdgeSnapshot =
            MapTerritoryEdgeSnapshot(targetId = edge.targetId)
    }
}

/**
 * Serialisierbarer Snapshot eines Kontinents aus der Map-Definition.
 */
@Serializable
data class MapContinentDefinitionSnapshot(
    val continentId: ContinentId,
    val territoryIds: List<TerritoryId>,
    val bonusValue: Int,
) {
    companion object {
        fun from(definition: ContinentDefinition): MapContinentDefinitionSnapshot =
            MapContinentDefinitionSnapshot(
                continentId = definition.continentId,
                territoryIds = definition.territoryIds,
                bonusValue = definition.bonusValue,
            )
    }
}

/**
 * Serialisierbarer Snapshot des mutierbaren Zustands eines Territoriums.
 */
@Serializable
data class MapTerritoryStateSnapshot(
    val territoryId: TerritoryId,
    val ownerId: PlayerId? = null,
    val troopCount: Int,
) {
    companion object {
        fun from(state: TerritoryState): MapTerritoryStateSnapshot =
            MapTerritoryStateSnapshot(
                territoryId = state.territoryId,
                ownerId = state.ownerId,
                troopCount = state.troopCount,
            )
    }
}

/**
 * Öffentliche Turn-State-Sicht innerhalb eines Full-Snapshots.
 */
@Serializable
data class PublicTurnStateSnapshot(
    val activePlayerId: PlayerId,
    val turnPhase: at.aau.pulverfass.shared.lobby.state.TurnPhase,
    val turnCount: Int,
    val startPlayerId: PlayerId,
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val pausedPlayerId: PlayerId? = null,
) {
    companion object {
        fun from(turnState: TurnState): PublicTurnStateSnapshot =
            PublicTurnStateSnapshot(
                activePlayerId = turnState.activePlayerId,
                turnPhase = turnState.turnPhase,
                turnCount = turnState.turnCount,
                startPlayerId = turnState.startPlayerId,
                isPaused = turnState.isPaused,
                pauseReason = turnState.pauseReason,
                pausedPlayerId = turnState.pausedPlayerId,
            )
    }
}

/**
 * Öffentliche Determinismus-Metadaten für Snapshot-/Reconnect-Pfade.
 */
@Serializable
data class PublicDeterminismMetadataSnapshot(
    val mapHash: String,
    val schemaVersion: Int,
    val mapId: String? = null,
    val seed: Long? = null,
    val rulesVersion: Int? = null,
) {
    companion object {
        fun from(definition: MapDefinition): PublicDeterminismMetadataSnapshot =
            PublicDeterminismMetadataSnapshot(
                mapHash = definition.mapHash,
                schemaVersion = definition.schemaVersion,
            )
    }
}

/**
 * Gemeinsame öffentliche Snapshot-Repräsentation des GameStates.
 *
 * Diese DTO ist die neutrale Quelle für alle öffentlichen Full-Snapshot-Payloads
 * wie Broadcasts, Catch-up-Antworten oder Map-Snapshots.
 */
@Serializable
data class PublicGameStateSnapshot(
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val determinism: PublicDeterminismMetadataSnapshot,
    val turnState: PublicTurnStateSnapshot,
    val definition: MapDefinitionSnapshot,
    val territoryStates: List<MapTerritoryStateSnapshot>,
)
