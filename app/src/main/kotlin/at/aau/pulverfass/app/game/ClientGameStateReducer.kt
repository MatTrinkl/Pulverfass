package at.aau.pulverfass.app.game

import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
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
            reinforcementState =
                if (event.nextPhase == TurnPhase.REINFORCEMENTS) {
                    current.reinforcementState
                } else {
                    ReinforcementUiState()
                },
            reinforcementPlacementAmount = 1,
            attackState =
                if (
                    current.attackState.latestResult != null &&
                    event.nextPhase != TurnPhase.REINFORCEMENTS
                ) {
                    current.attackState
                } else {
                    AttackUiState()
                },
            fortifyState = FortifyUiState(),
            selectedTradeInCardIds =
                if (event.nextPhase == TurnPhase.REINFORCEMENTS) {
                    current.selectedTradeInCardIds
                } else {
                    emptySet()
                },
            lastSyncError = null,
        )
    }

    fun applyTurnStateGetResponse(
        current: GameUiState,
        response: TurnStateGetResponse,
    ): GameUiState {
        return current.copy(
            activePlayerId = response.activePlayerId,
            turnPhase = response.turnPhase,
            turnCount = response.turnCount,
            startPlayerId = response.startPlayerId,
            isPaused = response.isPaused,
            pauseReason = response.pauseReason,
            pausedPlayerId = response.pausedPlayerId,
            reinforcementState = current.reinforcementStateFor(response.turnPhase),
            attackState = current.attackStateFor(response.turnPhase),
            fortifyState = current.fortifyStateFor(response),
            selectedTradeInCardIds = current.selectedTradeInCardsFor(response.turnPhase),
            isCatchingUp = false,
            lastSyncError = null,
        ).clearSelectionIf(current.shouldClearSelectionAfter(response))
    }

    private fun GameUiState.shouldClearSelectionAfter(response: TurnStateGetResponse): Boolean =
        response.turnPhase != turnPhase ||
            (response.turnPhase == TurnPhase.FORTIFY && response.fortifyUsedThisTurn)

    private fun GameUiState.reinforcementStateFor(turnPhase: TurnPhase): ReinforcementUiState =
        if (turnPhase == TurnPhase.REINFORCEMENTS) {
            reinforcementState
        } else {
            ReinforcementUiState()
        }

    private fun GameUiState.attackStateFor(turnPhase: TurnPhase): AttackUiState =
        if (turnPhase == TurnPhase.ATTACK) {
            attackState
        } else {
            AttackUiState()
        }

    private fun GameUiState.fortifyStateFor(response: TurnStateGetResponse): FortifyUiState =
        if (response.turnPhase == TurnPhase.FORTIFY) {
            fortifyState.copy(hasMoved = response.fortifyUsedThisTurn)
        } else {
            FortifyUiState()
        }

    private fun GameUiState.selectedTradeInCardsFor(turnPhase: TurnPhase): Set<CardId> =
        if (turnPhase == TurnPhase.REINFORCEMENTS) {
            selectedTradeInCardIds
        } else {
            emptySet()
        }

    private fun GameUiState.clearSelectionIf(shouldClearSelection: Boolean): GameUiState =
        if (shouldClearSelection) {
            copy(
                selectedRegionId = null,
                selectionFromRegionId = null,
                selectionToRegionId = null,
                selectionMessage = null,
            )
        } else {
            this
        }

    /**
     * Übernimmt den privaten Snapshot des lokalen Spielers.
     *
     * `handCards` bleibt für ältere Serverantworten als reine Anzeige
     * erhalten. `privateHandCards` enthält bei aktuellen Antworten zusätzlich
     * IDs und Typen; nur damit kann die UI einen Trade-in eindeutig senden.
     * Eine Auswahl wird mit dem neuen Snapshot geschnitten, damit keine
     * bereits eingetauschte oder anderweitig entfernte Karte auswählbar bleibt.
     */
    fun applyPrivateGetResponse(
        current: GameUiState,
        response: GameStatePrivateGetResponse,
    ): GameUiState =
        current.copy(
            handCards = response.handCards,
            privateHandCards =
                response.privateHandCards.map { card ->
                    PrivateHandCardUi(cardId = card.cardId, type = card.type)
                },
            selectedTradeInCardIds =
                current.selectedTradeInCardIds.intersect(
                    response.privateHandCards.map { it.cardId }.toSet(),
                ),
            secretObjectives = response.secretObjectives,
            lastSyncError = null,
        )

    /**
     * Übernimmt eine private Handaktualisierung nach einem erfolgreichen Trade-in.
     *
     * @param current bisheriger lokaler GameState
     * @param event nur für den betroffenen Spieler ausgelieferte neue Hand
     * @return State mit typisierten Karten und bereinigter lokaler Auswahl
     */
    fun applyPlayerHandUpdatedEvent(
        current: GameUiState,
        event: PlayerHandUpdatedEvent,
    ): GameUiState {
        val privateHandCards =
            event.handCards.map { card ->
                PrivateHandCardUi(cardId = card.cardId, type = card.type)
            }
        return current.copy(
            privateHandCards = privateHandCards,
            selectedTradeInCardIds =
                current.selectedTradeInCardIds.intersect(
                    privateHandCards.map(PrivateHandCardUi::cardId).toSet(),
                ),
            lastSyncError = null,
        )
    }

    /**
     * Baut die sichtbare Kartenprojektion mit einer aktualisierten Spielerliste
     * neu auf.
     *
     * Nach einem Reconnect können öffentlicher GameState und Lobby-Spieler in
     * unterschiedlicher Reihenfolge eintreffen. Der öffentliche Snapshot kennt
     * nur Owner-IDs, während Namen und Farben aus den Lobby-Spielern kommen.
     * Sobald diese Spieler nachgereicht werden, wird deshalb nur die UI-nahe
     * Regionendarstellung neu berechnet. Der serverautoritative Territory-State
     * bleibt unverändert.
     *
     * @param current bisheriger lokaler GameState
     * @param players aktuelle Lobby-Spielerliste für Owner-Farben und Namen
     * @return GameState mit aktualisierter Regionendarstellung
     */
    fun applyPlayers(
        current: GameUiState,
        players: List<LobbyPlayerUi>,
    ): GameUiState {
        if (current.territoryStates.isEmpty()) {
            return current
        }

        return current.copy(
            regionStates =
                buildRegionStates(
                    territoryStates = current.territoryStates,
                    players = players,
                ),
        )
    }

    /**
     * Verarbeitet die Auswahl einer Kartenregion nach einem Tap.
     *
     * Die UI liefert Android-Region-IDs aus der Farbhitmap. Der Reducer übersetzt
     * diese ID zuerst in die fachliche
     * [at.aau.pulverfass.shared.ids.TerritoryId], prüft dann den aktuellen
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

        if (current.turnPhase == TurnPhase.ATTACK) {
            return selectAttackRegion(
                current = current,
                regionId = regionId,
                territoryId = territoryId,
                territory = territory,
                localPlayerId = localPlayerId,
            )
        }

        if (current.turnPhase == TurnPhase.FORTIFY) {
            return selectFortifyRegion(
                current = current,
                regionId = regionId,
                territoryId = territoryId,
                territory = territory,
                localPlayerId = localPlayerId,
            )
        }

        /*
         * Die Platzierungs-UI öffnet nur für eigene Gebiete. Ein Tap auf ein
         * fremdes Gebiet wird bewusst ignoriert, statt einen Fehlerbanner zu
         * erzeugen oder ein bereits sinnvoll gewähltes Ziel zu verlieren.
         */
        if (
            current.turnPhase == TurnPhase.REINFORCEMENTS &&
            (localPlayerId == null || territory?.ownerId != localPlayerId)
        ) {
            return current.copy(selectionMessage = null)
        }

        if (current.turnPhase == TurnPhase.REINFORCEMENTS) {
            /*
             * Verstärkungen benötigen nur ein Zielgebiet. `selectionFrom` und
             * `selectionTo` gehören zu mehrstufigen Kartenaktionen und bleiben
             * hier leer, damit kein Zustand für eine nicht existierende
             * Start-/Zielbewegung vorgetäuscht wird.
             */
            return if (current.selectedRegionId == regionId) {
                current.copy(
                    selectedRegionId = null,
                    selectionFromRegionId = null,
                    selectionToRegionId = null,
                    selectionMessage = null,
                )
            } else {
                current.copy(
                    selectedRegionId = regionId,
                    selectionFromRegionId = null,
                    selectionToRegionId = null,
                    selectionMessage = null,
                )
            }
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

    /** Ändert die zu platzierende Anzahl innerhalb des bekannten Restpools. */
    fun adjustReinforcementPlacementAmount(
        current: GameUiState,
        delta: Int,
    ): GameUiState {
        val maxAmount = (current.reinforcementState.pendingAmount ?: 1).coerceAtLeast(1)
        return current.copy(
            reinforcementPlacementAmount =
                (current.reinforcementPlacementAmount + delta).coerceIn(1, maxAmount),
        )
    }

    /**
     * Ändert die für genau einen Angriff gebundene Truppenanzahl.
     *
     * Auf dem Ausgangsgebiet muss mindestens eine Truppe zurückbleiben. Die
     * Mindestbesetzung im Capture-Fall folgt der maximalen Würfelanzahl dieser
     * Angriffsabsicht, damit der Standardwert serverseitig gültig bleibt.
     */
    fun adjustAttackTroops(
        current: GameUiState,
        delta: Int,
    ): GameUiState {
        val maxAttackTroops = current.maximumAttackTroops() ?: return current
        val attackTroops =
            (current.attackState.attackTroops + delta).coerceIn(
                MIN_ATTACK_TROOPS,
                maxAttackTroops,
            )
        val minimumMove = minimumOccupyingTroopsForAttack(attackTroops)
        return current.copy(
            attackState =
                current.attackState.copy(
                    attackTroops = attackTroops,
                    moveAfterCapture =
                        current.attackState.moveAfterCapture.coerceIn(
                            minimumMove,
                            attackTroops,
                        ),
                ),
        )
    }

    /**
     * Ändert die gewünschte Besetzung eines eroberten Zielgebiets.
     *
     * Ein hoher Wunschwert kann nach serverseitigen Würfelverlusten ungültig
     * werden; in diesem Fall weist der Server den konkreten Angriff zurück.
     */
    fun adjustMoveAfterCapture(
        current: GameUiState,
        delta: Int,
    ): GameUiState {
        if (current.maximumAttackTroops() == null) {
            return current
        }
        val minimumMove = minimumOccupyingTroopsForAttack(current.attackState.attackTroops)
        return current.copy(
            attackState =
                current.attackState.copy(
                    moveAfterCapture =
                        (current.attackState.moveAfterCapture + delta).coerceIn(
                            minimumMove,
                            current.attackState.attackTroops,
                        ),
                ),
        )
    }

    /**
     * Ändert die lokal vorbereitete Fortify-Truppenanzahl innerhalb des Ausgangsgebiets.
     *
     * @param current bisheriger lokaler GameState mit gewählter Quelle
     * @param delta relative Änderung aus Slider oder Stepper
     * @return State mit auf das Ausgangsgebiet begrenzter Truppenanzahl
     */
    fun adjustFortifyTroops(
        current: GameUiState,
        delta: Int,
    ): GameUiState {
        val maxFortifyTroops = current.maximumFortifyTroops() ?: return current
        return current.copy(
            fortifyState =
                current.fortifyState.copy(
                    troopCount =
                        (current.fortifyState.troopCount + delta).coerceIn(
                            MIN_FORTIFY_TROOPS,
                            maxFortifyTroops,
                        ),
                ),
        )
    }

    /**
     * Markiert den erfolgreichen Fortify-Move lokal als verbraucht.
     *
     * Die sichtbaren Truppenzahlen folgen danach den öffentlichen Deltas. Der
     * lokale Marker verhindert nur einen zweiten Move, weil der Server dafür
     * aktuell kein öffentliches Flag im Turn-Snapshot mitschickt.
     *
     * @param current bisheriger lokaler GameState
     * @return State ohne Fortify-Auswahl und mit verbrauchtem Move
     */
    fun applyFortifyMoveAccepted(current: GameUiState): GameUiState =
        current.copy(
            selectedRegionId = null,
            selectionFromRegionId = null,
            selectionToRegionId = null,
            selectionMessage = null,
            fortifyState =
                current.fortifyState.copy(
                    troopCount = MIN_FORTIFY_TROOPS,
                    hasMoved = true,
                ),
        )

    /** Markiert maximal drei vorhandene private Karten für einen Trade-in. */
    fun toggleTradeInCard(
        current: GameUiState,
        cardId: CardId,
    ): GameUiState {
        if (current.privateHandCards.none { it.cardId == cardId }) {
            return current
        }
        if (cardId in current.selectedTradeInCardIds) {
            return current.copy(selectedTradeInCardIds = current.selectedTradeInCardIds - cardId)
        }
        if (current.selectedTradeInCardIds.size >= 3) {
            return current
        }
        return current.copy(selectedTradeInCardIds = current.selectedTradeInCardIds + cardId)
    }

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
                    reinforcementState =
                        if (turnState.turnPhase == TurnPhase.REINFORCEMENTS) {
                            ReinforcementUiState(
                                playerId = turnState.activePlayerId,
                                pendingAmount = turnState.pendingReinforcements,
                            )
                        } else {
                            ReinforcementUiState()
                        },
                    reinforcementPlacementAmount = 1,
                    attackState = AttackUiState(),
                    fortifyState =
                        if (turnState.turnPhase == TurnPhase.FORTIFY) {
                            FortifyUiState(hasMoved = turnState.fortifyUsedThisTurn)
                        } else {
                            FortifyUiState()
                        },
                    selectedTradeInCardIds =
                        if (turnState.turnPhase == TurnPhase.REINFORCEMENTS) {
                            current.selectedTradeInCardIds
                        } else {
                            emptySet()
                        },
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
            adjacentTerritoryIds =
                definition.territories.associate { territory ->
                    territory.territoryId to territory.edges.map { it.targetId }.toSet()
                },
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
                    reinforcementState =
                        if (event.turnPhase == TurnPhase.REINFORCEMENTS) {
                            current.reinforcementState
                        } else {
                            ReinforcementUiState()
                        },
                    reinforcementPlacementAmount = 1,
                    attackState =
                        if (
                            event.turnPhase == TurnPhase.ATTACK ||
                            (
                                current.attackState.latestResult != null &&
                                    event.turnPhase != TurnPhase.REINFORCEMENTS
                            )
                        ) {
                            current.attackState
                        } else {
                            AttackUiState()
                        },
                    fortifyState =
                        if (event.turnPhase == TurnPhase.FORTIFY) {
                            current.fortifyState
                        } else {
                            FortifyUiState()
                        },
                    selectedTradeInCardIds =
                        if (event.turnPhase == TurnPhase.REINFORCEMENTS) {
                            current.selectedTradeInCardIds
                        } else {
                            emptySet()
                        },
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
            is AttackResolvedBroadcastEvent ->
                current.copy(
                    selectedRegionId = null,
                    selectionFromRegionId = null,
                    selectionToRegionId = null,
                    selectionMessage = null,
                    attackState =
                        AttackUiState(
                            latestResult =
                                AttackResultUiState(
                                    fromTerritoryId = event.fromTerritoryId,
                                    toTerritoryId = event.toTerritoryId,
                                    attackerRolls = event.attackerRolls,
                                    defenderRolls = event.defenderRolls,
                                    attackerLosses = event.attackerLosses,
                                    defenderLosses = event.defenderLosses,
                                    attackerRemaining = event.attackerRemaining,
                                    defenderRemaining = event.defenderRemaining,
                                    occupyingTroopCount = event.occupyingTroopCount,
                                ),
                        ),
                )
            is ReinforcementsGrantedEvent -> current.applyReinforcementsGranted(event)
            is PendingReinforcementsChangedEvent -> current.applyPendingReinforcementsChanged(event)
            else -> current
        }

    private fun selectFortifyRegion(
        current: GameUiState,
        regionId: String,
        territoryId: TerritoryId,
        territory: GameTerritoryUiState?,
        localPlayerId: PlayerId?,
    ): GameUiState {
        if (localPlayerId == null || current.fortifyState.hasMoved) {
            return current.copy(selectionMessage = null)
        }

        val sourceRegionId = current.selectionFromRegionId
        return when {
            sourceRegionId == null ->
                current.selectFortifySourceIfPossible(regionId, territory, localPlayerId)
            sourceRegionId == regionId -> current.clearFortifySelection()
            current.isValidFortifyTarget(localPlayerId, sourceRegionId, territoryId) ->
                current.copy(
                    selectedRegionId = regionId,
                    selectionToRegionId = regionId,
                    selectionMessage = null,
                )
            territory.isFortifySourceFor(localPlayerId) -> current.withFortifySource(regionId)
            else -> current.copy(selectionMessage = null)
        }
    }

    private fun GameUiState.selectFortifySourceIfPossible(
        regionId: String,
        territory: GameTerritoryUiState?,
        localPlayerId: PlayerId,
    ): GameUiState =
        if (territory.isFortifySourceFor(localPlayerId)) {
            withFortifySource(regionId)
        } else {
            copy(selectionMessage = null)
        }

    private fun GameTerritoryUiState?.isFortifySourceFor(playerId: PlayerId): Boolean =
        this?.ownerId == playerId && troopCount > MIN_FORTIFY_TROOPS

    private fun GameUiState.withFortifySource(regionId: String): GameUiState =
        copy(
            selectedRegionId = regionId,
            selectionFromRegionId = regionId,
            selectionToRegionId = null,
            selectionMessage = null,
            fortifyState = fortifyState.copy(troopCount = MIN_FORTIFY_TROOPS),
        )

    private fun GameUiState.clearFortifySelection(): GameUiState =
        copy(
            selectedRegionId = null,
            selectionFromRegionId = null,
            selectionToRegionId = null,
            selectionMessage = null,
            fortifyState = fortifyState.copy(troopCount = MIN_FORTIFY_TROOPS),
        )

    private fun GameUiState.isValidFortifyTarget(
        playerId: PlayerId,
        sourceRegionId: String,
        targetTerritoryId: TerritoryId,
    ): Boolean {
        val sourceTerritoryId = GameMapTerritoryMapper.toTerritoryId(sourceRegionId)
        return sourceTerritoryId != targetTerritoryId &&
            territoryStates[targetTerritoryId]?.ownerId == playerId &&
            hasOwnedPath(playerId, sourceTerritoryId, targetTerritoryId)
    }

    private fun GameUiState.hasOwnedPath(
        playerId: PlayerId,
        sourceTerritoryId: TerritoryId,
        targetTerritoryId: TerritoryId,
    ): Boolean {
        if (
            territoryStates[sourceTerritoryId]?.ownerId != playerId ||
            territoryStates[targetTerritoryId]?.ownerId != playerId
        ) {
            return false
        }

        val visited = linkedSetOf(sourceTerritoryId)
        val queue = ArrayDeque<TerritoryId>()
        queue.add(sourceTerritoryId)

        while (queue.isNotEmpty()) {
            val currentTerritoryId = queue.removeFirst()
            adjacentTerritoryIds[currentTerritoryId].orEmpty().forEach { neighborId ->
                if (
                    neighborId !in visited &&
                    territoryStates[neighborId]?.ownerId == playerId
                ) {
                    if (neighborId == targetTerritoryId) {
                        return true
                    }
                    visited.add(neighborId)
                    queue.add(neighborId)
                }
            }
        }

        return false
    }

    private fun selectAttackRegion(
        current: GameUiState,
        regionId: String,
        territoryId: TerritoryId,
        territory: GameTerritoryUiState?,
        localPlayerId: PlayerId?,
    ): GameUiState {
        val isAvailableSource =
            localPlayerId != null &&
                territory?.ownerId == localPlayerId &&
                territory.troopCount > MIN_ATTACK_TROOPS
        val sourceRegionId = current.selectionFromRegionId

        if (sourceRegionId == null) {
            return if (isAvailableSource) {
                current.withAttackSource(regionId)
            } else {
                current.copy(selectionMessage = null)
            }
        }
        if (sourceRegionId == regionId) {
            return current.clearAttackSelection()
        }
        if (isAvailableSource) {
            return current.withAttackSource(regionId)
        }

        val sourceTerritoryId = GameMapTerritoryMapper.toTerritoryId(sourceRegionId)
        val isEnemyNeighbor =
            territory != null &&
                territory.ownerId != localPlayerId &&
                territoryId in current.adjacentTerritoryIds[sourceTerritoryId].orEmpty()
        return if (isEnemyNeighbor) {
            current.copy(
                selectedRegionId = regionId,
                selectionToRegionId = regionId,
                selectionMessage = null,
            )
        } else {
            current.copy(selectionMessage = null)
        }
    }

    private fun GameUiState.withAttackSource(regionId: String): GameUiState =
        copy(
            selectedRegionId = regionId,
            selectionFromRegionId = regionId,
            selectionToRegionId = null,
            selectionMessage = null,
            attackState =
                attackState.copy(
                    attackTroops = MIN_ATTACK_TROOPS,
                    moveAfterCapture = minimumOccupyingTroopsForAttack(MIN_ATTACK_TROOPS),
                ),
        )

    private fun GameUiState.clearAttackSelection(): GameUiState =
        copy(
            selectedRegionId = null,
            selectionFromRegionId = null,
            selectionToRegionId = null,
            selectionMessage = null,
            attackState =
                attackState.copy(
                    attackTroops = MIN_ATTACK_TROOPS,
                    moveAfterCapture = minimumOccupyingTroopsForAttack(MIN_ATTACK_TROOPS),
                ),
        )

    private fun GameUiState.maximumAttackTroops(): Int? {
        val sourceRegionId = selectionFromRegionId ?: return null
        val sourceTerritoryId = GameMapTerritoryMapper.toTerritoryId(sourceRegionId)
        val maximum = territoryStates[sourceTerritoryId]?.troopCount?.minus(1) ?: return null
        return maximum.takeIf { it >= MIN_ATTACK_TROOPS }
    }

    private fun GameUiState.maximumFortifyTroops(): Int? {
        val sourceRegionId = selectionFromRegionId ?: return null
        val sourceTerritoryId = GameMapTerritoryMapper.toTerritoryId(sourceRegionId)
        val maximum = territoryStates[sourceTerritoryId]?.troopCount?.minus(1) ?: return null
        return maximum.takeIf { it >= MIN_FORTIFY_TROOPS }
    }

    private fun GameUiState.applyReinforcementsGranted(
        event: ReinforcementsGrantedEvent,
    ): GameUiState {
        /*
         * Zu Beginn einer Verstärkungsphase liefert der Server einen Basisgrant
         * mit Gebiets-/Kontinentbonus. Ein späterer Kartentausch liefert einen
         * zusätzlichen Grant mit ausschließlich Kartenbonus; dessen Betrag
         * wird durch das folgende PendingReinforcementsChangedEvent zum
         * bestehenden Restpool addiert. Ein Basisgrant darf deshalb einen
         * vorherigen Snapshot mit Restpool 0 ersetzen, ein Kartengrant nicht.
         */
        val isAdditionalCardGrant =
            event.cardBonus > 0 &&
                event.territoryBonus == 0 &&
                event.continentBonus == 0
        val continuesCurrentPool =
            reinforcementState.playerId == event.playerId &&
                reinforcementState.pendingAmount != null &&
                isAdditionalCardGrant
        val updatedReinforcements =
            if (continuesCurrentPool) {
                reinforcementState.copy(
                    territoryBonus = reinforcementState.territoryBonus + event.territoryBonus,
                    continentBonus = reinforcementState.continentBonus + event.continentBonus,
                    cardBonus = reinforcementState.cardBonus + event.cardBonus,
                )
            } else {
                ReinforcementUiState(
                    playerId = event.playerId,
                    pendingAmount = event.amount,
                    territoryBonus = event.territoryBonus,
                    continentBonus = event.continentBonus,
                    cardBonus = event.cardBonus,
                    isBonusBreakdownKnown = true,
                )
            }
        val maxPlacement = (updatedReinforcements.pendingAmount ?: 1).coerceAtLeast(1)

        return copy(
            reinforcementState = updatedReinforcements,
            reinforcementPlacementAmount = reinforcementPlacementAmount.coerceIn(1, maxPlacement),
        )
    }

    private fun GameUiState.applyPendingReinforcementsChanged(
        event: PendingReinforcementsChangedEvent,
    ): GameUiState {
        /*
         * Der Server bleibt für den Restpool autoritativ: Platzierungen senden
         * negative Deltas, Kartentausch positive. Events anderer Spieler oder
         * Deltas vor der initialen Grant-/Snapshot-Basis dürfen den lokalen
         * Bedienzustand nicht verändern.
         */
        if (reinforcementState.playerId != event.playerId) {
            return this
        }
        val currentAmount = reinforcementState.pendingAmount ?: return this
        val updatedAmount = (currentAmount + event.delta).coerceAtLeast(0)
        return copy(
            reinforcementState = reinforcementState.copy(pendingAmount = updatedAmount),
            reinforcementPlacementAmount =
                reinforcementPlacementAmount.coerceIn(1, updatedAmount.coerceAtLeast(1)),
        )
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
