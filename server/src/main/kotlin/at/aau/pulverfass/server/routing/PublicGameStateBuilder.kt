package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.StartPlayerConfigured
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.lobby.event.VisibleGameStatePayload
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicGameStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot

/**
 * Zentrale Projektion des autoritativen GameStates in ausschließlich öffentliche
 * Snapshot- und Delta-Payloads.
 *
 * Alle öffentlichen Netzwerkmodelle laufen bewusst über diese Klasse, damit
 * Mappinglogik nicht mehrfach implementiert wird und keine privaten Payloads in
 * öffentliche DTOs gelangen.
 */
class PublicGameStateBuilder {
    /**
     * Baut einen vollständigen öffentlichen Snapshot des aktuellen GameStates.
     *
     * @throws IllegalStateException wenn der GameState noch keinen Turn-State
     * oder keine MapDefinition enthält
     */
    fun buildSnapshot(gameState: GameState): PublicGameStateSnapshot {
        val resolvedTurnState =
            gameState.resolvedTurnState
                ?: throw IllegalStateException("GameState enthält keinen TurnState für einen Snapshot.")
        val mapProjection = buildMapProjection(gameState)

        return PublicGameStateSnapshot(
            lobbyCode = gameState.lobbyCode,
            stateVersion = gameState.stateVersion,
            determinism = mapProjection.determinism,
            turnState = PublicTurnStateSnapshot.from(resolvedTurnState),
            definition = mapProjection.definition,
            territoryStates = mapProjection.territoryStates,
        )
    }

    /**
     * Baut die ältere Map-spezifische Snapshot-Response aus derselben
     * öffentlichen Projektion wie Full-Snapshots.
     */
    fun buildMapGetResponse(gameState: GameState): MapGetResponse {
        val mapProjection = buildMapProjection(gameState)

        return MapGetResponse(
            lobbyCode = gameState.lobbyCode,
            schemaVersion = mapProjection.determinism.schemaVersion,
            mapHash = mapProjection.determinism.mapHash,
            stateVersion = gameState.stateVersion,
            definition = mapProjection.definition,
            territoryStates = mapProjection.territoryStates,
        )
    }

    /** Baut eine Catch-up-Response als vollständigen öffentlichen Snapshot. */
    fun buildCatchUpResponse(gameState: GameState): GameStateCatchUpResponse =
        GameStateCatchUpResponse.from(buildSnapshot(gameState))

    /** Baut den öffentlichen Turnwechsel-/Self-Heal-Broadcast. */
    fun buildSnapshotBroadcast(gameState: GameState): GameStateSnapshotBroadcast =
        GameStateSnapshotBroadcast.from(buildSnapshot(gameState))

    /**
     * Baut ein Delta für genau ein fachliches Event.
     *
     * Für Einzelevents ist `fromVersion == toVersion`, weil die Version bereits
     * nach Anwendung des Events im [currentState] enthalten ist.
     */
    fun buildDelta(
        lobbyCode: LobbyCode,
        event: LobbyEvent,
        previousState: GameState,
        currentState: GameState,
    ): GameStateDeltaEvent? =
        buildDelta(
            lobbyCode = lobbyCode,
            fromVersion = currentState.stateVersion,
            toVersion = currentState.stateVersion,
            payloads = buildPublicPayloads(lobbyCode, event, previousState, currentState),
        )

    /**
     * Baut ein Delta aus bereits aufbereiteter sichtbarer Payload-Liste.
     *
     * Nicht-öffentliche Payloads werden aktiv abgewiesen, damit keine privaten
     * Inhalte in öffentliche Delta-Broadcasts geraten.
     */
    fun buildDelta(
        lobbyCode: LobbyCode,
        fromVersion: Long,
        toVersion: Long,
        payloads: List<VisibleGameStatePayload>,
    ): GameStateDeltaEvent? {
        if (payloads.isEmpty()) {
            return null
        }

        val nonPublicEvents = payloads.filterNot { it is PublicGameEvent }
        require(nonPublicEvents.isEmpty()) {
            val leakedTypes = nonPublicEvents.joinToString { it::class.simpleName ?: it::class.qualifiedName ?: "unknown" }
            "GameStateDeltaEvent darf nur PublicGameEvent enthalten. Nicht-oeffentliche Payloads: $leakedTypes."
        }

        @Suppress("UNCHECKED_CAST")
        return GameStateDeltaEvent(
            lobbyCode = lobbyCode,
            fromVersion = fromVersion,
            toVersion = toVersion,
            events = payloads as List<PublicGameEvent>,
        )
    }

    /**
     * Leitet aus einem Domain-Event die sichtbaren öffentlichen
     * Netzwerk-Payloads ab.
     *
     * Die Funktion enthält bewusst auch Fallback-Projektionen für Fälle, in
     * denen kein direkt sendbares Event vorliegt, der öffentliche Turn-State
     * sich aber trotzdem geändert hat.
     */
    fun buildPublicPayloads(
        lobbyCode: LobbyCode,
        event: LobbyEvent,
        previousState: GameState,
        currentState: GameState,
    ): List<PublicGameEvent> =
        buildList {
            if (event is at.aau.pulverfass.shared.lobby.event.GameStarted) {
                add(GameStartedEvent(lobbyCode))
                currentState.turnState?.let { add(it.toUpdatedEvent(lobbyCode)) }
                return@buildList
            }

            if (event is StartPlayerConfigured) {
                currentState.turnState?.let { add(it.toUpdatedEvent(lobbyCode)) }
                return@buildList
            }

            when (event) {
                is TerritoryOwnerChangedEvent -> add(event.copy(stateVersion = currentState.stateVersion))
                is TerritoryTroopsChangedEvent -> add(event.copy(stateVersion = currentState.stateVersion))
                is TurnStateUpdatedEvent -> add(event)
                else -> {
                    if (previousState.turnState != currentState.turnState) {
                        currentState.turnState?.let { add(it.toUpdatedEvent(lobbyCode)) }
                    }
                    if (!previousState.gameStarted && currentState.gameStarted) {
                        add(0, GameStartedEvent(lobbyCode))
                    }
                }
            }
        }

    private fun at.aau.pulverfass.shared.lobby.state.TurnState.toUpdatedEvent(
        lobbyCode: LobbyCode,
    ): TurnStateUpdatedEvent =
        TurnStateUpdatedEvent(
            lobbyCode = lobbyCode,
            activePlayerId = activePlayerId,
            turnPhase = turnPhase,
            turnCount = turnCount,
            startPlayerId = startPlayerId,
            isPaused = isPaused,
            pauseReason = pauseReason,
            pausedPlayerId = pausedPlayerId,
        )

    private fun buildMapProjection(gameState: GameState): PublicMapProjection {
        val definition =
            gameState.mapDefinition
                ?: throw IllegalStateException("GameState enthält keine MapDefinition für einen Snapshot.")

        return PublicMapProjection(
            determinism = PublicDeterminismMetadataSnapshot.from(definition),
            definition = MapDefinitionSnapshot.from(definition),
            territoryStates = gameState.allTerritoryStates().map(MapTerritoryStateSnapshot::from),
        )
    }

    private data class PublicMapProjection(
        val determinism: PublicDeterminismMetadataSnapshot,
        val definition: MapDefinitionSnapshot,
        val territoryStates: List<MapTerritoryStateSnapshot>,
    )
}
