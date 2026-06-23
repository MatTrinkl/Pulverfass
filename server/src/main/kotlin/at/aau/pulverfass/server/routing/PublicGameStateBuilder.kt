package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.MatchEndedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsSetEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.StartPlayerConfigured
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.BaseReinforcementRuleEngine
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.MatchEndedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
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
                ?: throw IllegalStateException(
                    "GameState enthält keinen TurnState für einen Snapshot.",
                )
        val mapProjection = buildMapProjection(gameState)

        return PublicGameStateSnapshot(
            lobbyCode = gameState.lobbyCode,
            stateVersion = gameState.stateVersion,
            determinism = mapProjection.determinism,
            turnState =
                PublicTurnStateSnapshot.from(
                    turnState = resolvedTurnState,
                    pendingReinforcements =
                        gameState.pendingReinforcementsFor(resolvedTurnState.activePlayerId),
                    fortifyUsedThisTurn = gameState.fortifyUsedThisTurn,
                ),
            definition = mapProjection.definition,
            territoryStates = mapProjection.territoryStates,
            gameStatus = gameState.status,
            matchEndReason = gameState.closedReason,
            winnerPlayerId = gameState.winnerPlayerId,
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
            val leakedTypes =
                nonPublicEvents.joinToString {
                    it::class.simpleName ?: it::class.qualifiedName ?: "unknown"
                }
            "GameStateDeltaEvent darf nur PublicGameEvent enthalten. " +
                "Nicht-oeffentliche Payloads: $leakedTypes."
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
        buildLifecyclePayloads(lobbyCode, event, currentState)
            ?: buildEventPayloads(lobbyCode, event, previousState, currentState)
            ?: buildFallbackPayloads(lobbyCode, previousState, currentState)

    private fun buildLifecyclePayloads(
        lobbyCode: LobbyCode,
        event: LobbyEvent,
        currentState: GameState,
    ): List<PublicGameEvent>? =
        when (event) {
            is at.aau.pulverfass.shared.lobby.event.GameStarted ->
                buildList {
                    add(GameStartedEvent(lobbyCode))
                    addAll(turnStatePayloads(lobbyCode, currentState))
                }
            is StartPlayerConfigured -> turnStatePayloads(lobbyCode, currentState)
            else -> null
        }

    private fun buildEventPayloads(
        lobbyCode: LobbyCode,
        event: LobbyEvent,
        previousState: GameState,
        currentState: GameState,
    ): List<PublicGameEvent>? =
        when (event) {
            is CardSetTradedInEvent ->
                listOf(
                    ReinforcementsGrantedEvent(
                        lobbyCode = lobbyCode,
                        playerId = event.playerId,
                        amount = event.value,
                        territoryBonus = 0,
                        continentBonus = 0,
                        cardBonus = event.value,
                    ),
                )
            is PendingReinforcementsChangedEvent -> listOf(event)
            is PendingReinforcementsSetEvent ->
                listOf(buildPendingReinforcementsPayload(lobbyCode, event, currentState))
            is PlayerEliminatedEvent ->
                buildList {
                    add(event.copy(stateVersion = currentState.stateVersion))
                    if (previousState.turnState != currentState.turnState) {
                        addAll(turnStatePayloads(lobbyCode, currentState))
                    }
                }
            is AttackResolvedEvent -> buildAttackPayloads(event, currentState.stateVersion)
            is TerritoryOwnerChangedEvent,
            is TerritoryTroopsChangedEvent,
            -> listOf(versionedTerritoryEvent(event, currentState.stateVersion))
            is FortifyMoveAppliedEvent -> buildFortifyMovePayloads(currentState, event)
            is FortifyUsedSetEvent -> emptyList()
            is MatchEndedEvent ->
                listOf(
                    MatchEndedBroadcastEvent(
                        lobbyCode = lobbyCode,
                        reason = event.reason,
                        winnerPlayerId = event.winnerPlayerId,
                        stateVersion = currentState.stateVersion,
                    ),
                )
            is TurnStateUpdatedEvent -> listOf(event)
            else -> null
        }

    private fun buildPendingReinforcementsPayload(
        lobbyCode: LobbyCode,
        event: PendingReinforcementsSetEvent,
        currentState: GameState,
    ): ReinforcementsGrantedEvent {
        val breakdown =
            BaseReinforcementRuleEngine.computeBaseReinforcements(
                playerId = event.playerId,
                state = currentState,
            )
        return ReinforcementsGrantedEvent(
            lobbyCode = lobbyCode,
            playerId = event.playerId,
            amount = event.amount,
            territoryBonus = breakdown.territoryBonus,
            continentBonus = breakdown.continentBonus,
            cardBonus = event.amount - breakdown.total,
        )
    }

    private fun buildFallbackPayloads(
        lobbyCode: LobbyCode,
        previousState: GameState,
        currentState: GameState,
    ): List<PublicGameEvent> =
        buildList {
            if (previousState.turnState != currentState.turnState) {
                addAll(turnStatePayloads(lobbyCode, currentState))
            }
            if (!previousState.gameStarted && currentState.gameStarted) {
                add(0, GameStartedEvent(lobbyCode))
            }
        }

    private fun buildFortifyMovePayloads(
        currentState: GameState,
        event: FortifyMoveAppliedEvent,
    ): List<PublicGameEvent> =
        listOf(
            TerritoryTroopsChangedEvent(
                lobbyCode = event.lobbyCode,
                territoryId = event.fromTerritoryId,
                troopCount = currentState.troopCountOf(event.fromTerritoryId),
                stateVersion = currentState.stateVersion,
            ),
            TerritoryTroopsChangedEvent(
                lobbyCode = event.lobbyCode,
                territoryId = event.toTerritoryId,
                troopCount = currentState.troopCountOf(event.toTerritoryId),
                stateVersion = currentState.stateVersion,
            ),
        )

    private fun turnStatePayloads(
        lobbyCode: LobbyCode,
        currentState: GameState,
    ): List<PublicGameEvent> =
        currentState.turnState
            ?.let { listOf(it.toUpdatedEvent(lobbyCode)) }
            .orEmpty()

    private fun versionedTerritoryEvent(
        event: LobbyEvent,
        stateVersion: Long,
    ): PublicGameEvent =
        when (event) {
            is TerritoryOwnerChangedEvent -> event.copy(stateVersion = stateVersion)
            is TerritoryTroopsChangedEvent -> event.copy(stateVersion = stateVersion)
            else ->
                throw IllegalArgumentException(
                    "Unsupported territory event: ${event::class.simpleName}",
                )
        }

    private fun buildAttackPayloads(
        event: AttackResolvedEvent,
        stateVersion: Long,
    ): List<PublicGameEvent> =
        buildList {
            fun requireOccupyingTroopCount(): Int =
                requireNotNull(event.occupyingTroopCount) {
                    "Capture-AttackResolvedEvent benötigt occupyingTroopCount."
                }
            add(AttackResolvedBroadcastEvent.from(event, stateVersion))
            add(
                TerritoryTroopsChangedEvent(
                    lobbyCode = event.lobbyCode,
                    territoryId = event.fromTerritoryId,
                    troopCount =
                        if (event.capture) {
                            event.attackerRemaining - requireOccupyingTroopCount()
                        } else {
                            event.attackerRemaining
                        },
                    stateVersion = stateVersion,
                ),
            )
            if (event.capture) {
                add(
                    TerritoryOwnerChangedEvent(
                        lobbyCode = event.lobbyCode,
                        territoryId = event.toTerritoryId,
                        ownerId = event.attackerPlayerId,
                        stateVersion = stateVersion,
                    ),
                )
                add(
                    TerritoryTroopsChangedEvent(
                        lobbyCode = event.lobbyCode,
                        territoryId = event.toTerritoryId,
                        troopCount = requireOccupyingTroopCount(),
                        stateVersion = stateVersion,
                    ),
                )
            } else {
                add(
                    TerritoryTroopsChangedEvent(
                        lobbyCode = event.lobbyCode,
                        territoryId = event.toTerritoryId,
                        troopCount = event.defenderRemaining,
                        stateVersion = stateVersion,
                    ),
                )
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
                ?: throw IllegalStateException(
                    "GameState enthält keine MapDefinition für einen Snapshot.",
                )

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
