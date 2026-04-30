package at.aau.pulverfass.app.game

import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse

/**
 * Reduziert Server-Payloads auf den lokalen Android-GameState.
 *
 * Snapshot, Delta und Turn-State laufen hier über dieselben Invarianten:
 * veraltete Snapshots werden ignoriert, Delta-Lücken lösen Catch-up aus und
 * Territory-Updates schreiben immer zuerst den shared-basierten Zustand.
 */
object ClientGameStateReducer {
    /**
     * Übernimmt den initialen oder manuell angeforderten Map-Snapshot.
     *
     * @param current bisheriger lokaler GameState
     * @param response autoritative Map-Antwort des Servers
     * @param players aktuelle Lobby-Spielerliste für Owner-Farben und Namen
     * @return aktualisierter UI-State oder der alte State bei veraltetem Snapshot
     */
    fun applyMapGetResponse(
        current: GameUiState,
        response: MapGetResponse,
        players: List<LobbyPlayerUi>,
    ): GameUiState {
        if (response.stateVersion < current.stateVersion) {
            return current.copy(isCatchingUp = false)
        }

        return applyMapState(
            current = current,
            stateVersion = response.stateVersion,
            determinism =
                PublicDeterminismMetadataSnapshot(
                    mapHash = response.mapHash,
                    schemaVersion = response.schemaVersion,
                ),
            definition = response.definition,
            territoryStates = response.territoryStates,
            players = players,
        )
    }

    fun applySnapshotBroadcast(
        current: GameUiState,
        response: GameStateSnapshotBroadcast,
        players: List<LobbyPlayerUi>,
    ): GameUiState =
        applyFullSnapshot(
            current = current,
            stateVersion = response.stateVersion,
            determinism = response.determinism,
            turnState = response.turnState,
            definition = response.definition,
            territoryStates = response.territoryStates,
            players = players,
        )

    fun applyCatchUpResponse(
        current: GameUiState,
        response: GameStateCatchUpResponse,
        players: List<LobbyPlayerUi>,
    ): GameUiState =
        applyFullSnapshot(
            current = current,
            stateVersion = response.stateVersion,
            determinism = response.determinism,
            turnState = response.turnState,
            definition = response.definition,
            territoryStates = response.territoryStates,
            players = players,
        )

    /**
     * Wendet ein öffentliches Delta auf den lokalen State an.
     *
     * Deltas dürfen nur angewendet werden, wenn ihre `fromVersion` exakt zur
     * lokalen [GameUiState.stateVersion] passt. Bei jeder Lücke wird bewusst kein
     * Teilupdate geraten, sondern Catch-up angefordert, damit die Karte wieder
     * vollständig serverautoritativen Zustand bekommt.
     *
     * @param current bisheriger lokaler GameState
     * @param delta serverseitiges Delta mit Versionsfenster
     * @param players aktuelle Lobby-Spielerliste für Owner-Farben und Namen
     * @return neuer State plus Hinweis, ob ein Catch-up nötig ist
     */
    fun applyDelta(
        current: GameUiState,
        delta: GameStateDeltaEvent,
        players: List<LobbyPlayerUi>,
    ): DeltaApplyResult {
        if (delta.toVersion <= current.stateVersion) {
            return DeltaApplyResult(state = current, needsCatchUp = false)
        }
        if (!canApplyDelta(localVersion = current.stateVersion, delta = delta)) {
            return DeltaApplyResult(
                state =
                    current.copy(
                        isCatchingUp = true,
                        isDesynced = true,
                        lastSyncError = "Delta-Lücke erkannt. Synchronisiere Spielstand neu.",
                    ),
                needsCatchUp = true,
            )
        }

        val nextState =
            delta.events.fold(current) { state, event ->
                applyPublicEvent(current = state, event = event, players = players)
            }

        return DeltaApplyResult(
            state =
                nextState.copy(
                    isCatchingUp = false,
                    isDesynced = false,
                    stateVersion = delta.toVersion,
                    lastSyncError = null,
                ),
            needsCatchUp = false,
        )
    }

    /**
     * Aktualisiert reine Turn-/Phaseninformationen aus einem Boundary-Event.
     *
     * Eine Phasengrenze beendet die lokale Territory-Auswahl, weil ein zuvor
     * ausgewähltes `from/to` in der nächsten Phase fachlich anders interpretiert
     * werden könnte.
     *
     * @param current bisheriger lokaler GameState
     * @param event serverseitiger Phasenwechsel
     * @return aktualisierter UI-State
     */
    fun applyPhaseBoundary(
        current: GameUiState,
        event: PhaseBoundaryEvent,
    ): GameUiState {
        if (event.stateVersion < current.stateVersion) {
            return current
        }

        return current.copy(
            stateVersion = event.stateVersion,
            activePlayerId = event.activePlayerId,
            turnPhase = event.nextPhase,
            turnCount = event.turnCount,
            selectedRegionId = null,
            selectionFromRegionId = null,
            selectionToRegionId = null,
            selectionMessage = null,
            isCatchingUp = false,
            isDesynced = false,
            lastSyncError = null,
        )
    }

    fun applyTurnStateGetResponse(
        current: GameUiState,
        response: TurnStateGetResponse,
    ): GameUiState =
        current.copy(
            activePlayerId = response.activePlayerId,
            turnPhase = response.turnPhase,
            turnCount = response.turnCount,
            startPlayerId = response.startPlayerId,
            isPaused = response.isPaused,
            pauseReason = response.pauseReason,
            pausedPlayerId = response.pausedPlayerId,
            isCatchingUp = false,
            lastSyncError = null,
        )

    fun applyPrivateGetResponse(
        current: GameUiState,
        response: GameStatePrivateGetResponse,
    ): GameUiState =
        current.copy(
            handCards = response.handCards,
            secretObjectives = response.secretObjectives,
            lastSyncError = null,
        )

    /**
     * Verarbeitet die Auswahl einer Kartenregion nach einem Tap.
     *
     * Die UI liefert Android-Region-IDs aus der Farbhitmap. Der Reducer übersetzt
     * diese ID zuerst in die fachliche [TerritoryId], prüft dann den aktuellen
     * TurnState und hält anschließend die einfache `from/to`-Auswahl.
     *
     * @param current bisheriger lokaler GameState
     * @param regionId Android-Region-ID aus der Karten-Hitdetection
     * @param localPlayerId eigener Spieler, falls der Server ihn schon bestätigt hat
     * @return aktualisierter Auswahlzustand
     */
    fun selectRegion(
        current: GameUiState,
        regionId: String,
        localPlayerId: PlayerId?,
    ): GameUiState {
        val territoryId = GameMapTerritoryMapper.toTerritoryId(regionId)
        val territory = current.territoryStates[territoryId]

        if (
            current.turnPhase == TurnPhase.REINFORCEMENTS &&
            (localPlayerId == null || territory?.ownerId != localPlayerId)
        ) {
            return current.copy(
                selectionMessage =
                    "In der Verstärkungsphase kannst du nur eigene Gebiete auswählen.",
            )
        }

        return when {
            current.selectionFromRegionId == null ->
                current.copy(
                    selectedRegionId = regionId,
                    selectionFromRegionId = regionId,
                    selectionToRegionId = null,
                    selectionMessage = "Ausgangsgebiet ausgewählt.",
                )
            current.selectionFromRegionId == regionId ->
                current.copy(
                    selectedRegionId = null,
                    selectionFromRegionId = null,
                    selectionToRegionId = null,
                    selectionMessage = null,
                )
            else ->
                current.copy(
                    selectedRegionId = regionId,
                    selectionToRegionId = regionId,
                    selectionMessage = "Zielgebiet ausgewählt.",
                )
        }
    }

    fun toggleCards(current: GameUiState): GameUiState =
        current.copy(cardsVisible = !current.cardsVisible)

    /**
     * Ersetzt öffentliche Map-, Turn- und Determinismusdaten vollständig.
     *
     * Full Snapshots sind der sichere Zielzustand nach Spielstart, Reconnect und
     * Desync-Recovery. Darum wird lokale Auswahl verworfen und die UI danach als
     * synchron markiert.
     */
    private fun applyFullSnapshot(
        current: GameUiState,
        stateVersion: Long,
        determinism: PublicDeterminismMetadataSnapshot,
        turnState: PublicTurnStateSnapshot,
        definition: MapDefinitionSnapshot,
        territoryStates: List<MapTerritoryStateSnapshot>,
        players: List<LobbyPlayerUi>,
    ): GameUiState {
        if (stateVersion < current.stateVersion) {
            return current.copy(isCatchingUp = false)
        }

        return applyMapState(
            current =
                current.copy(
                    isStarted = true,
                    activePlayerId = turnState.activePlayerId,
                    turnPhase = turnState.turnPhase,
                    turnCount = turnState.turnCount,
                    startPlayerId = turnState.startPlayerId,
                    isPaused = turnState.isPaused,
                    pauseReason = turnState.pauseReason,
                    pausedPlayerId = turnState.pausedPlayerId,
                    selectedRegionId = null,
                    selectionFromRegionId = null,
                    selectionToRegionId = null,
                    selectionMessage = null,
                ),
            stateVersion = stateVersion,
            determinism = determinism,
            definition = definition,
            territoryStates = territoryStates,
            players = players,
        )
    }

    private fun applyMapState(
        current: GameUiState,
        stateVersion: Long,
        determinism: PublicDeterminismMetadataSnapshot,
        definition: MapDefinitionSnapshot,
        territoryStates: List<MapTerritoryStateSnapshot>,
        players: List<LobbyPlayerUi>,
    ): GameUiState {
        val territories = territorySnapshotsToUiStates(territoryStates)
        return current.copy(
            stateVersion = stateVersion,
            schemaVersion = determinism.schemaVersion,
            mapHash = determinism.mapHash,
            definitionTerritoryIds = definition.territories.map { it.territoryId },
            territoryStates = territories,
            regionStates = buildRegionStates(territoryStates = territories, players = players),
            isCatchingUp = false,
            isDesynced = false,
            lastSyncError = null,
        )
    }

    private fun applyPublicEvent(
        current: GameUiState,
        event: PublicGameEvent,
        players: List<LobbyPlayerUi>,
    ): GameUiState =
        when (event) {
            is GameStartedEvent ->
                current.copy(isStarted = true, isCatchingUp = true, lastSyncError = null)
            is TurnStateUpdatedEvent ->
                current.copy(
                    activePlayerId = event.activePlayerId,
                    turnPhase = event.turnPhase,
                    turnCount = event.turnCount,
                    startPlayerId = event.startPlayerId,
                    isPaused = event.isPaused,
                    pauseReason = event.pauseReason,
                    pausedPlayerId = event.pausedPlayerId,
                    selectedRegionId = null,
                    selectionFromRegionId = null,
                    selectionToRegionId = null,
                    selectionMessage = null,
                    lastSyncError = null,
                )
            is TerritoryOwnerChangedEvent ->
                current.updateTerritory(players = players, event = event)
            is TerritoryTroopsChangedEvent ->
                current.updateTerritory(players = players, event = event)
            else -> current
        }

    private fun GameUiState.updateTerritory(
        players: List<LobbyPlayerUi>,
        event: TerritoryOwnerChangedEvent,
    ): GameUiState {
        val previous =
            territoryStates[event.territoryId]
                ?: GameTerritoryUiState(
                    territoryId = event.territoryId,
                    ownerId = null,
                    troopCount = 0,
                )
        val updatedTerritories =
            territoryStates + (event.territoryId to previous.copy(ownerId = event.ownerId))

        return copy(
            territoryStates = updatedTerritories,
            regionStates = buildRegionStates(updatedTerritories, players),
        )
    }

    private fun GameUiState.updateTerritory(
        players: List<LobbyPlayerUi>,
        event: TerritoryTroopsChangedEvent,
    ): GameUiState {
        val previous =
            territoryStates[event.territoryId]
                ?: GameTerritoryUiState(
                    territoryId = event.territoryId,
                    ownerId = null,
                    troopCount = 0,
                )
        val updatedTerritories =
            territoryStates + (event.territoryId to previous.copy(troopCount = event.troopCount))

        return copy(
            territoryStates = updatedTerritories,
            regionStates = buildRegionStates(updatedTerritories, players),
        )
    }

    private fun canApplyDelta(
        localVersion: Long,
        delta: GameStateDeltaEvent,
    ): Boolean =
        delta.fromVersion == localVersion &&
            delta.toVersion > localVersion
}

/**
 * Ergebnis eines Delta-Reduces inklusive Hinweis, ob ein Full Snapshot nötig ist.
 */
data class DeltaApplyResult(
    val state: GameUiState,
    val needsCatchUp: Boolean,
)
