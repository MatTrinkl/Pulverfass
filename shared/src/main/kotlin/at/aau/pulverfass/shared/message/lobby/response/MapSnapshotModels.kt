package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TerritoryState
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

internal fun GameState.toMapGetResponse(): MapGetResponse {
    val definition =
        mapDefinition
            ?: throw IllegalStateException("GameState enthält keine MapDefinition für einen Snapshot.")
    val definitionSnapshot = MapDefinitionSnapshot.from(definition)

    return MapGetResponse(
        lobbyCode = lobbyCode,
        schemaVersion = definition.schemaVersion,
        mapHash = definition.mapHash,
        stateVersion = stateVersion,
        definition = definitionSnapshot,
        territoryStates = allTerritoryStates().map(MapTerritoryStateSnapshot::from),
    )
}

internal fun GameState.toTurnStateGetResponse(): TurnStateGetResponse {
    val resolvedTurnState =
        resolvedTurnState
            ?: throw IllegalStateException("GameState enthält keinen TurnState für einen Snapshot.")

    return TurnStateGetResponse(
        lobbyCode = lobbyCode,
        activePlayerId = resolvedTurnState.activePlayerId,
        turnPhase = resolvedTurnState.turnPhase,
        turnCount = resolvedTurnState.turnCount,
        startPlayerId = resolvedTurnState.startPlayerId,
        isPaused = resolvedTurnState.isPaused,
        pauseReason = resolvedTurnState.pauseReason,
        pausedPlayerId = resolvedTurnState.pausedPlayerId,
    )
}
