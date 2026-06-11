package at.aau.pulverfass.app.game

import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prüft den zentralen Reducer, der Backend-Events in den lokalen Game-UI-State überführt.
 *
 * Die Tests bilden Snapshots, Deltas, Phasenwechsel, private Karten, Verstärkungen,
 * Angriffe und Verschiebungen ab. Dadurch bleibt sichtbar, welche Serverdaten der
 * Client direkt übernimmt und welche lokalen UI-Auswahlen bewusst erhalten bleiben.
 */
class ClientGameStateReducerTest {
    @Test
    fun `catch up snapshot replaces public map and turn state`() {
        val response =
            GameStateCatchUpResponse(
                lobbyCode = lobbyCode,
                stateVersion = 3,
                determinism = determinism,
                turnState =
                    PublicTurnStateSnapshot(
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = aliceId,
                        pendingReinforcements = 4,
                    ),
                definition = mapDefinition("brasilien"),
                territoryStates =
                    listOf(
                        MapTerritoryStateSnapshot(
                            territoryId = TerritoryId("brasilien"),
                            ownerId = aliceId,
                            troopCount = 5,
                        ),
                    ),
            )

        val state =
            ClientGameStateReducer.applyCatchUpResponse(
                current = GameUiState(isCatchingUp = true),
                response = response,
                players = players,
            )

        assertTrue(state.isStarted)
        assertFalse(state.isCatchingUp)
        assertEquals(3, state.stateVersion)
        assertEquals(TurnPhase.REINFORCEMENTS, state.turnPhase)
        assertEquals(aliceId, state.reinforcementState.playerId)
        assertEquals(4, state.reinforcementState.pendingAmount)
        assertFalse(state.reinforcementState.isBonusBreakdownKnown)
        assertTrue(state.canManageReinforcements(aliceId))
        assertEquals(5, state.regionStates.getValue("brazil").troopCount)
        assertEquals("Alice", state.regionStates.getValue("brazil").ownerName)
    }

    @Test
    fun `map get response replaces map state and ignores stale snapshots`() {
        val current = GameUiState(stateVersion = 3, isCatchingUp = true)
        val stale =
            ClientGameStateReducer.applyMapGetResponse(
                current = current,
                response =
                    MapGetResponse(
                        lobbyCode = lobbyCode,
                        schemaVersion = 1,
                        mapHash = "old",
                        stateVersion = 2,
                        definition = mapDefinition("brasilien"),
                        territoryStates = emptyList(),
                    ),
                players = players,
            )

        val updated =
            ClientGameStateReducer.applyMapGetResponse(
                current = current,
                response =
                    MapGetResponse(
                        lobbyCode = lobbyCode,
                        schemaVersion = 1,
                        mapHash = "new",
                        stateVersion = 4,
                        definition = mapDefinition("brasilien"),
                        territoryStates =
                            listOf(
                                MapTerritoryStateSnapshot(
                                    territoryId = TerritoryId("brasilien"),
                                    ownerId = aliceId,
                                    troopCount = 6,
                                ),
                            ),
                    ),
                players = players,
            )

        assertEquals(3, stale.stateVersion)
        assertFalse(stale.isCatchingUp)
        assertEquals(4, updated.stateVersion)
        assertEquals("new", updated.mapHash)
        assertEquals(6, updated.regionStates.getValue("brazil").troopCount)
    }

    @Test
    fun `snapshot broadcast replaces started state and clears local selection`() {
        val state =
            ClientGameStateReducer.applySnapshotBroadcast(
                current =
                    GameUiState(
                        selectedRegionId = "brazil",
                        selectionFromRegionId = "brazil",
                        selectionToRegionId = "argentina",
                        selectionMessage = "local",
                    ),
                response =
                    GameStateSnapshotBroadcast(
                        lobbyCode = lobbyCode,
                        stateVersion = 5,
                        determinism = determinism,
                        turnState =
                            PublicTurnStateSnapshot(
                                activePlayerId = bobId,
                                turnPhase = TurnPhase.FORTIFY,
                                turnCount = 2,
                                startPlayerId = aliceId,
                                isPaused = true,
                                pauseReason = "waiting",
                                pausedPlayerId = bobId,
                                fortifyUsedThisTurn = true,
                            ),
                        definition = mapDefinition("brasilien"),
                        territoryStates =
                            listOf(
                                MapTerritoryStateSnapshot(
                                    territoryId = TerritoryId("brasilien"),
                                    ownerId = bobId,
                                    troopCount = 7,
                                ),
                            ),
                    ),
                players = players,
            )

        assertTrue(state.isStarted)
        assertEquals(bobId, state.activePlayerId)
        assertEquals(TurnPhase.FORTIFY, state.turnPhase)
        assertTrue(state.isPaused)
        assertTrue(state.fortifyState.hasMoved)
        assertFalse(state.canManageFortify(bobId))
        assertEquals(null, state.selectedRegionId)
        assertEquals("Bob", state.regionStates.getValue("brazil").ownerName)
    }

    @Test
    fun `delta updates territory state when version follows local state`() {
        val base =
            GameUiState(
                stateVersion = 1,
                territoryStates =
                    mapOf(
                        TerritoryId("brasilien") to
                            GameTerritoryUiState(
                                territoryId = TerritoryId("brasilien"),
                                ownerId = aliceId,
                                troopCount = 3,
                            ),
                    ),
            )
        val delta =
            GameStateDeltaEvent(
                lobbyCode = lobbyCode,
                fromVersion = 1,
                toVersion = 2,
                events =
                    listOf(
                        TerritoryTroopsChangedEvent(
                            lobbyCode = lobbyCode,
                            territoryId = TerritoryId("brasilien"),
                            troopCount = 8,
                            stateVersion = 2,
                        ),
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = bobId,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = aliceId,
                        ),
                    ),
            )

        val result = ClientGameStateReducer.applyDelta(base, delta, players)

        assertFalse(result.needsCatchUp)
        assertEquals(2, result.state.stateVersion)
        assertEquals(8, result.state.regionStates.getValue("brazil").troopCount)
        assertEquals(bobId, result.state.activePlayerId)
        assertEquals(TurnPhase.ATTACK, result.state.turnPhase)
    }

    @Test
    fun `delta applies start and owner events and ignores unrelated public events`() {
        val delta =
            GameStateDeltaEvent(
                lobbyCode = lobbyCode,
                fromVersion = 1,
                toVersion = 2,
                events =
                    listOf(
                        GameStartedEvent(lobbyCode),
                        TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("brasilien"), aliceId),
                        object : PublicGameEvent {},
                    ),
            )

        val result =
            ClientGameStateReducer.applyDelta(
                current = GameUiState(stateVersion = 1, lastSyncError = "old"),
                delta = delta,
                players = players,
            )

        assertFalse(result.needsCatchUp)
        assertTrue(result.state.isStarted)
        assertEquals(
            aliceId,
            result.state.territoryStates.getValue(TerritoryId("brasilien")).ownerId,
        )
        assertEquals("Alice", result.state.regionStates.getValue("brazil").ownerName)
        assertEquals(null, result.state.lastSyncError)
    }

    @Test
    fun `duplicate delta is ignored without requesting catch up`() {
        val base = GameUiState(stateVersion = 3)
        val delta =
            GameStateDeltaEvent(
                lobbyCode = lobbyCode,
                fromVersion = 2,
                toVersion = 3,
                events =
                    listOf(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = bobId,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = aliceId,
                        ),
                    ),
            )

        val result = ClientGameStateReducer.applyDelta(base, delta, players)

        assertFalse(result.needsCatchUp)
        assertEquals(base, result.state)
    }

    @Test
    fun `delta version gap marks state as desynced and requests catch up`() {
        val delta =
            GameStateDeltaEvent(
                lobbyCode = lobbyCode,
                fromVersion = 4,
                toVersion = 4,
                events =
                    listOf(
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = aliceId,
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 1,
                            startPlayerId = aliceId,
                        ),
                    ),
            )

        val result =
            ClientGameStateReducer.applyDelta(
                current = GameUiState(stateVersion = 2),
                delta = delta,
                players = players,
            )

        assertTrue(result.needsCatchUp)
        assertTrue(result.state.isDesynced)
        assertTrue(result.state.isCatchingUp)
    }

    @Test
    fun `reinforcement selection accepts only own territories`() {
        val ownRegion = "brazil"
        val enemyRegion = "argentina"
        val state =
            GameUiState(
                activePlayerId = aliceId,
                turnPhase = TurnPhase.REINFORCEMENTS,
                territoryStates =
                    mapOf(
                        TerritoryId("brasilien") to
                            GameTerritoryUiState(
                                territoryId = TerritoryId("brasilien"),
                                ownerId = aliceId,
                                troopCount = 3,
                            ),
                        TerritoryId("argentinien") to
                            GameTerritoryUiState(
                                territoryId = TerritoryId("argentinien"),
                                ownerId = bobId,
                                troopCount = 2,
                            ),
                    ),
            )

        val rejected =
            ClientGameStateReducer.selectRegion(
                current = state,
                regionId = enemyRegion,
                localPlayerId = aliceId,
            )
        val accepted =
            ClientGameStateReducer.selectRegion(
                current = state,
                regionId = ownRegion,
                localPlayerId = aliceId,
            )
        val retainedAfterEnemyTap =
            ClientGameStateReducer.selectRegion(
                current = accepted,
                regionId = enemyRegion,
                localPlayerId = aliceId,
            )
        val dismissed =
            ClientGameStateReducer.selectRegion(
                current = accepted,
                regionId = ownRegion,
                localPlayerId = aliceId,
            )

        assertEquals(null, rejected.selectedRegionId)
        assertEquals(null, rejected.selectionMessage)
        assertEquals(ownRegion, accepted.selectedRegionId)
        assertEquals(null, accepted.selectionFromRegionId)
        assertEquals(null, accepted.selectionMessage)
        assertEquals(ownRegion, retainedAfterEnemyTap.selectedRegionId)
        assertEquals(null, retainedAfterEnemyTap.selectionMessage)
        assertEquals(null, dismissed.selectedRegionId)
    }

    @Test
    fun `attack selection accepts only usable sources and adjacent enemy targets`() {
        val base =
            GameUiState(
                activePlayerId = aliceId,
                turnPhase = TurnPhase.ATTACK,
                adjacentTerritoryIds =
                    mapOf(
                        TerritoryId("brasilien") to setOf(TerritoryId("argentinien")),
                    ),
                territoryStates =
                    mapOf(
                        TerritoryId("brasilien") to
                            GameTerritoryUiState(TerritoryId("brasilien"), aliceId, 5),
                        TerritoryId("argentinien") to
                            GameTerritoryUiState(TerritoryId("argentinien"), bobId, 2),
                        TerritoryId("mittelamerika") to
                            GameTerritoryUiState(TerritoryId("mittelamerika"), bobId, 2),
                        TerritoryId("usa") to
                            GameTerritoryUiState(TerritoryId("usa"), aliceId, 2),
                    ),
            )

        val weakSource =
            ClientGameStateReducer.selectRegion(base, "america", aliceId)
        val selectedSource =
            ClientGameStateReducer.selectRegion(base, "brazil", aliceId)
        val ignoredTarget =
            ClientGameStateReducer.selectRegion(selectedSource, "mexico", aliceId)
        val selectedTarget =
            ClientGameStateReducer.selectRegion(selectedSource, "argentina", aliceId)
        val increasedAttack =
            ClientGameStateReducer.adjustAttackTroops(selectedTarget, 20)
        val increasedOccupation =
            ClientGameStateReducer.adjustMoveAfterCapture(increasedAttack, 20)
        val dismissed =
            ClientGameStateReducer.selectRegion(selectedTarget, "brazil", aliceId)

        assertEquals(null, weakSource.selectionFromRegionId)
        assertEquals("brazil", selectedSource.selectionFromRegionId)
        assertEquals(2, selectedSource.attackState.moveAfterCapture)
        assertEquals(null, ignoredTarget.selectionToRegionId)
        assertEquals("argentina", selectedTarget.selectionToRegionId)
        assertTrue(selectedTarget.canSubmitAttack(aliceId))
        assertFalse(
            selectedTarget
                .copy(attackState = selectedTarget.attackState.copy(moveAfterCapture = 1))
                .canSubmitAttack(aliceId),
        )
        assertTrue(selectedTarget.canConfirmAttackDone(aliceId))
        assertFalse(selectedTarget.canRequestTurnAdvance(aliceId))
        assertEquals(4, increasedAttack.attackState.attackTroops)
        assertEquals(3, increasedAttack.attackState.moveAfterCapture)
        assertEquals(4, increasedOccupation.attackState.moveAfterCapture)
        assertEquals(null, dismissed.selectionFromRegionId)
    }

    @Test
    fun `attack availability ignores weak sources and allows departed owners`() {
        val departedPlayer = PlayerId(99)
        val weakSourceState =
            GameUiState(
                activePlayerId = aliceId,
                turnPhase = TurnPhase.ATTACK,
                adjacentTerritoryIds =
                    mapOf(
                        TerritoryId("brasilien") to setOf(TerritoryId("argentinien")),
                    ),
                territoryStates =
                    mapOf(
                        TerritoryId("brasilien") to
                            GameTerritoryUiState(TerritoryId("brasilien"), aliceId, 2),
                        TerritoryId("argentinien") to
                            GameTerritoryUiState(TerritoryId("argentinien"), departedPlayer, 3),
                    ),
            )
        val abandonedTargetState =
            weakSourceState.copy(
                territoryStates =
                    weakSourceState.territoryStates +
                        (
                            TerritoryId("brasilien") to
                                GameTerritoryUiState(TerritoryId("brasilien"), aliceId, 3)
                        ),
            )
        val emptyDepartedTargetState =
            abandonedTargetState.copy(
                territoryStates =
                    abandonedTargetState.territoryStates +
                        (
                            TerritoryId("argentinien") to
                                GameTerritoryUiState(TerritoryId("argentinien"), departedPlayer, 0)
                        ),
            )

        assertFalse(weakSourceState.hasAvailableAttack(aliceId))
        assertTrue(abandonedTargetState.hasAvailableAttack(aliceId))
        assertFalse(emptyDepartedTargetState.hasAvailableAttack(aliceId))
    }

    @Test
    fun `attack selection rejects neutral targets but allows departed owners`() {
        val sourceId = TerritoryId("brasilien")
        val targetId = TerritoryId("argentinien")
        val base =
            GameUiState(
                activePlayerId = aliceId,
                turnPhase = TurnPhase.ATTACK,
                adjacentTerritoryIds = mapOf(sourceId to setOf(targetId)),
                territoryStates =
                    mapOf(
                        sourceId to GameTerritoryUiState(sourceId, aliceId, 3),
                        targetId to GameTerritoryUiState(targetId, null, 3),
                    ),
            )

        val selectedSource = ClientGameStateReducer.selectRegion(base, "brazil", aliceId)
        val rejectedNeutralTarget =
            ClientGameStateReducer.selectRegion(selectedSource, "argentina", aliceId)
        val departedTarget =
            selectedSource.copy(
                territoryStates =
                    selectedSource.territoryStates +
                        (targetId to GameTerritoryUiState(targetId, PlayerId(99), 3)),
            )
        val acceptedDepartedTarget =
            ClientGameStateReducer.selectRegion(departedTarget, "argentina", aliceId)
        val emptyDepartedTarget =
            selectedSource.copy(
                territoryStates =
                    selectedSource.territoryStates +
                        (targetId to GameTerritoryUiState(targetId, PlayerId(99), 0)),
            )
        val rejectedEmptyDepartedTarget =
            ClientGameStateReducer.selectRegion(emptyDepartedTarget, "argentina", aliceId)

        assertEquals(null, rejectedNeutralTarget.selectionToRegionId)
        assertFalse(rejectedNeutralTarget.canSubmitAttack(aliceId))
        assertEquals("argentina", acceptedDepartedTarget.selectionToRegionId)
        assertTrue(acceptedDepartedTarget.canSubmitAttack(aliceId))
        assertEquals(null, rejectedEmptyDepartedTarget.selectionToRegionId)
        assertFalse(rejectedEmptyDepartedTarget.canSubmitAttack(aliceId))
    }

    @Test
    fun `attack submission rejects troop count above current source limit`() {
        val sourceId = TerritoryId("brasilien")
        val targetId = TerritoryId("argentinien")
        val selectedAttack =
            GameUiState(
                activePlayerId = aliceId,
                turnPhase = TurnPhase.ATTACK,
                selectedRegionId = "argentina",
                selectionFromRegionId = "brazil",
                selectionToRegionId = "argentina",
                adjacentTerritoryIds = mapOf(sourceId to setOf(targetId)),
                territoryStates =
                    mapOf(
                        sourceId to GameTerritoryUiState(sourceId, aliceId, 5),
                        targetId to GameTerritoryUiState(targetId, PlayerId(99), 1),
                    ),
                attackState =
                    AttackUiState(
                        attackTroops = 5,
                        moveAfterCapture = 3,
                    ),
            )

        assertFalse(selectedAttack.canSubmitAttack(aliceId))
        assertTrue(
            selectedAttack
                .copy(attackState = AttackUiState(attackTroops = 4, moveAfterCapture = 3))
                .canSubmitAttack(aliceId),
        )
    }

    @Test
    fun `fortify selection accepts only owned connected targets and marks move as consumed`() {
        val base =
            GameUiState(
                activePlayerId = aliceId,
                turnPhase = TurnPhase.FORTIFY,
                adjacentTerritoryIds =
                    mapOf(
                        TerritoryId("brasilien") to setOf(TerritoryId("kanada")),
                        TerritoryId("kanada") to
                            setOf(
                                TerritoryId("brasilien"),
                                TerritoryId("groenland"),
                            ),
                        TerritoryId("groenland") to setOf(TerritoryId("kanada")),
                    ),
                territoryStates =
                    mapOf(
                        TerritoryId("brasilien") to
                            GameTerritoryUiState(TerritoryId("brasilien"), aliceId, 5),
                        TerritoryId("kanada") to
                            GameTerritoryUiState(TerritoryId("kanada"), aliceId, 2),
                        TerritoryId("groenland") to
                            GameTerritoryUiState(TerritoryId("groenland"), aliceId, 1),
                        TerritoryId("mittelamerika") to
                            GameTerritoryUiState(TerritoryId("mittelamerika"), aliceId, 2),
                        TerritoryId("argentinien") to
                            GameTerritoryUiState(TerritoryId("argentinien"), bobId, 2),
                        TerritoryId("usa") to
                            GameTerritoryUiState(TerritoryId("usa"), aliceId, 1),
                    ),
            )

        val weakSource =
            ClientGameStateReducer.selectRegion(base, "america", aliceId)
        val selectedSource =
            ClientGameStateReducer.selectRegion(base, "brazil", aliceId)
        val ignoredEnemyTarget =
            ClientGameStateReducer.selectRegion(selectedSource, "argentina", aliceId)
        val ignoredDisconnectedTarget =
            ClientGameStateReducer.selectRegion(selectedSource, "mexico", aliceId)
        val selectedTarget =
            ClientGameStateReducer.selectRegion(selectedSource, "greenland", aliceId)
        val increasedMove =
            ClientGameStateReducer.adjustFortifyTroops(selectedTarget, 20)
        val acceptedMove =
            ClientGameStateReducer.applyFortifyMoveAccepted(increasedMove)
        val retainedFortifyState =
            ClientGameStateReducer.applyTurnStateGetResponse(
                current = acceptedMove,
                response =
                    TurnStateGetResponse(
                        lobbyCode = lobbyCode,
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.FORTIFY,
                        turnCount = 1,
                        startPlayerId = aliceId,
                        fortifyUsedThisTurn = true,
                    ),
            )
        val serverConsumedSelection =
            ClientGameStateReducer.applyTurnStateGetResponse(
                current = selectedTarget,
                response =
                    TurnStateGetResponse(
                        lobbyCode = lobbyCode,
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.FORTIFY,
                        turnCount = 1,
                        startPlayerId = aliceId,
                        fortifyUsedThisTurn = true,
                    ),
            )
        val resetFortifyState =
            ClientGameStateReducer.applyTurnStateGetResponse(
                current = acceptedMove,
                response =
                    TurnStateGetResponse(
                        lobbyCode = lobbyCode,
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.DRAW_CARD,
                        turnCount = 1,
                        startPlayerId = aliceId,
                    ),
            )
        val ignoredAfterMove =
            ClientGameStateReducer.selectRegion(retainedFortifyState, "brazil", aliceId)

        assertEquals(null, weakSource.selectionFromRegionId)
        assertEquals("brazil", selectedSource.selectionFromRegionId)
        assertEquals(null, ignoredEnemyTarget.selectionToRegionId)
        assertEquals(null, ignoredDisconnectedTarget.selectionToRegionId)
        assertEquals("greenland", selectedTarget.selectionToRegionId)
        assertTrue(selectedTarget.canSubmitFortifyMove(aliceId))
        assertTrue(selectedTarget.canRequestTurnAdvance(aliceId))
        assertEquals(4, increasedMove.fortifyState.troopCount)
        assertEquals(null, acceptedMove.selectionFromRegionId)
        assertTrue(acceptedMove.fortifyState.hasMoved)
        assertTrue(retainedFortifyState.fortifyState.hasMoved)
        assertTrue(serverConsumedSelection.fortifyState.hasMoved)
        assertEquals(null, serverConsumedSelection.selectedRegionId)
        assertEquals(null, serverConsumedSelection.selectionFromRegionId)
        assertEquals(null, serverConsumedSelection.selectionToRegionId)
        assertFalse(serverConsumedSelection.canSubmitFortifyMove(aliceId))
        assertFalse(resetFortifyState.fortifyState.hasMoved)
        assertEquals(null, ignoredAfterMove.selectionFromRegionId)
    }

    @Test
    fun `fortify submission rejects stale invalid selections before sending`() {
        val sourceId = TerritoryId("brasilien")
        val middleId = TerritoryId("kanada")
        val targetId = TerritoryId("groenland")
        val selectedMove =
            GameUiState(
                activePlayerId = aliceId,
                turnPhase = TurnPhase.FORTIFY,
                selectedRegionId = "greenland",
                selectionFromRegionId = "brazil",
                selectionToRegionId = "greenland",
                adjacentTerritoryIds =
                    mapOf(
                        sourceId to setOf(middleId),
                        middleId to setOf(sourceId, targetId),
                        targetId to setOf(middleId),
                    ),
                territoryStates =
                    mapOf(
                        sourceId to GameTerritoryUiState(sourceId, aliceId, 4),
                        middleId to GameTerritoryUiState(middleId, aliceId, 1),
                        targetId to GameTerritoryUiState(targetId, aliceId, 1),
                    ),
                fortifyState = FortifyUiState(troopCount = 3),
            )
        val targetChangedOwner =
            selectedMove.copy(
                territoryStates =
                    selectedMove.territoryStates +
                        (targetId to GameTerritoryUiState(targetId, bobId, 1)),
            )
        val pathBecameBlocked =
            selectedMove.copy(
                territoryStates =
                    selectedMove.territoryStates +
                        (middleId to GameTerritoryUiState(middleId, bobId, 1)),
            )
        val sourceTroopsReduced =
            selectedMove.copy(
                territoryStates =
                    selectedMove.territoryStates +
                        (sourceId to GameTerritoryUiState(sourceId, aliceId, 3)),
            )
        val noMovableSourceTroops =
            selectedMove.copy(
                territoryStates =
                    selectedMove.territoryStates +
                        (sourceId to GameTerritoryUiState(sourceId, aliceId, 1)),
            )

        assertTrue(selectedMove.canSubmitFortifyMove(aliceId))
        assertFalse(targetChangedOwner.canSubmitFortifyMove(aliceId))
        assertFalse(pathBecameBlocked.canSubmitFortifyMove(aliceId))
        assertFalse(sourceTroopsReduced.canSubmitFortifyMove(aliceId))
        assertFalse(noMovableSourceTroops.canSubmitFortifyMove(aliceId))
    }

    @Test
    fun `fortify availability requires movable own troops and owned path`() {
        val sourceId = TerritoryId("brasilien")
        val middleId = TerritoryId("kanada")
        val targetId = TerritoryId("groenland")
        val base =
            GameUiState(
                activePlayerId = aliceId,
                turnPhase = TurnPhase.FORTIFY,
                adjacentTerritoryIds =
                    mapOf(
                        sourceId to setOf(middleId),
                        middleId to setOf(sourceId, targetId),
                        targetId to setOf(middleId),
                    ),
                territoryStates =
                    mapOf(
                        sourceId to GameTerritoryUiState(sourceId, aliceId, 1),
                        middleId to GameTerritoryUiState(middleId, aliceId, 1),
                        targetId to GameTerritoryUiState(targetId, aliceId, 1),
                    ),
            )
        val movableSource =
            base.copy(
                territoryStates =
                    base.territoryStates +
                        (sourceId to GameTerritoryUiState(sourceId, aliceId, 2)),
            )
        val blockedPath =
            movableSource.copy(
                territoryStates =
                    movableSource.territoryStates +
                        (middleId to GameTerritoryUiState(middleId, bobId, 1)),
            )

        assertFalse(base.hasAvailableFortify(aliceId))
        assertTrue(movableSource.hasAvailableFortify(aliceId))
        assertFalse(blockedPath.hasAvailableFortify(aliceId))
        assertFalse(
            movableSource.copy(fortifyState = FortifyUiState(hasMoved = true))
                .hasAvailableFortify(aliceId),
        )
    }

    @Test
    fun `attack result is taken from public event and clears the prepared selection`() {
        val result =
            ClientGameStateReducer.applyDelta(
                current =
                    GameUiState(
                        stateVersion = 1,
                        turnPhase = TurnPhase.ATTACK,
                        selectedRegionId = "argentina",
                        selectionFromRegionId = "brazil",
                        selectionToRegionId = "argentina",
                    ),
                delta =
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 1,
                        toVersion = 2,
                        events =
                            listOf(
                                AttackResolvedBroadcastEvent(
                                    lobbyCode = lobbyCode,
                                    attackerPlayerId = aliceId,
                                    defenderPlayerId = bobId,
                                    fromTerritoryId = TerritoryId("brasilien"),
                                    toTerritoryId = TerritoryId("argentinien"),
                                    attackTroops = 3,
                                    sourceTroopsBefore = 5,
                                    targetTroopsBefore = 1,
                                    requestedAttackDice = 3,
                                    attackDice = 2,
                                    defendDice = 1,
                                    attackerRolls = listOf(6, 4),
                                    defenderRolls = listOf(2),
                                    attackerLosses = 0,
                                    defenderLosses = 1,
                                    attackerRemaining = 3,
                                    defenderRemaining = 0,
                                    occupyingTroopCount = 2,
                                ),
                            ),
                    ),
                players = players,
            ).state

        val battle = requireNotNull(result.attackState.latestResult)
        val afterAutomaticAdvance =
            ClientGameStateReducer.applyPhaseBoundary(
                result,
                PhaseBoundaryEvent(
                    lobbyCode = lobbyCode,
                    stateVersion = 2,
                    previousPhase = TurnPhase.ATTACK,
                    nextPhase = TurnPhase.FORTIFY,
                    activePlayerId = aliceId,
                    turnCount = 1,
                ),
            )
        assertEquals(null, result.selectionFromRegionId)
        assertEquals(listOf(6, 4), battle.attackerRolls)
        assertEquals(2, battle.occupyingTroopCount)
        assertTrue(battle.captured)
        assertEquals(battle, afterAutomaticAdvance.attackState.latestResult)
    }

    @Test
    fun `auto attack result keeps route for next attack until target is captured`() {
        val sourceId = TerritoryId("brasilien")
        val targetId = TerritoryId("argentinien")
        val result =
            ClientGameStateReducer.applyDelta(
                current =
                    GameUiState(
                        stateVersion = 1,
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.ATTACK,
                        selectedRegionId = "argentina",
                        selectionFromRegionId = "brazil",
                        selectionToRegionId = "argentina",
                        adjacentTerritoryIds = mapOf(sourceId to setOf(targetId)),
                        territoryStates =
                            mapOf(
                                sourceId to GameTerritoryUiState(sourceId, aliceId, 5),
                                targetId to GameTerritoryUiState(targetId, bobId, 3),
                            ),
                        attackState =
                            AttackUiState(
                                attackTroops = 3,
                                moveAfterCapture = 3,
                                autoAttack =
                                    AutoAttackUiState(
                                        intent =
                                            AutoAttackIntent(
                                                fromTerritoryId = sourceId,
                                                toTerritoryId = targetId,
                                                attackTroops = 3,
                                                moveAfterCapture = 3,
                                            ),
                                        isEnabled = true,
                                        isAwaitingResult = true,
                                        pendingRequestId = "auto-attack-1",
                                    ),
                            ),
                    ),
                delta =
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 1,
                        toVersion = 2,
                        events =
                            listOf(
                                AttackResolvedBroadcastEvent(
                                    lobbyCode = lobbyCode,
                                    attackerPlayerId = aliceId,
                                    defenderPlayerId = bobId,
                                    fromTerritoryId = sourceId,
                                    toTerritoryId = targetId,
                                    attackTroops = 3,
                                    sourceTroopsBefore = 5,
                                    targetTroopsBefore = 3,
                                    requestedAttackDice = 3,
                                    attackDice = 3,
                                    defendDice = 2,
                                    attackerRolls = listOf(6, 3, 1),
                                    defenderRolls = listOf(5, 2),
                                    attackerLosses = 1,
                                    defenderLosses = 1,
                                    attackerRemaining = 4,
                                    defenderRemaining = 2,
                                ),
                                TerritoryTroopsChangedEvent(
                                    lobbyCode = lobbyCode,
                                    territoryId = sourceId,
                                    troopCount = 4,
                                    stateVersion = 2,
                                ),
                                TerritoryTroopsChangedEvent(
                                    lobbyCode = lobbyCode,
                                    territoryId = targetId,
                                    troopCount = 2,
                                    stateVersion = 2,
                                ),
                            ),
                    ),
                players = players,
            ).state

        assertEquals("brazil", result.selectionFromRegionId)
        assertEquals("argentina", result.selectionToRegionId)
        assertEquals("argentina", result.selectedRegionId)
        assertFalse(result.attackState.latestResult?.captured ?: true)
        assertTrue(result.attackState.autoAttack.isEnabled)
        assertFalse(result.attackState.autoAttack.isAwaitingResult)
        assertEquals(null, result.attackState.autoAttack.pendingRequestId)
    }

    @Test
    fun `catch up during auto attack keeps intent ready for next request`() {
        val sourceId = TerritoryId("brasilien")
        val targetId = TerritoryId("argentinien")
        val intent =
            AutoAttackIntent(
                fromTerritoryId = sourceId,
                toTerritoryId = targetId,
                attackTroops = 2,
                moveAfterCapture = 2,
            )
        val result =
            ClientGameStateReducer.applyCatchUpResponse(
                current =
                    GameUiState(
                        stateVersion = 11,
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.ATTACK,
                        selectedRegionId = "argentina",
                        selectionFromRegionId = "brazil",
                        selectionToRegionId = "argentina",
                        adjacentTerritoryIds = mapOf(sourceId to setOf(targetId)),
                        territoryStates =
                            mapOf(
                                sourceId to GameTerritoryUiState(sourceId, aliceId, 5),
                                targetId to GameTerritoryUiState(targetId, bobId, 1),
                            ),
                        attackState =
                            AttackUiState(
                                attackTroops = 2,
                                moveAfterCapture = 2,
                                autoAttack =
                                    AutoAttackUiState(
                                        intent = intent,
                                        isEnabled = true,
                                        isAwaitingResult = true,
                                        pendingRequestId = "auto-attack-5",
                                        errorText = "old",
                                    ),
                            ),
                        isCatchingUp = true,
                    ),
                response =
                    GameStateCatchUpResponse(
                        lobbyCode = lobbyCode,
                        stateVersion = 12,
                        determinism = determinism,
                        turnState =
                            PublicTurnStateSnapshot(
                                activePlayerId = aliceId,
                                turnPhase = TurnPhase.ATTACK,
                                turnCount = 1,
                                startPlayerId = aliceId,
                            ),
                        definition =
                            MapDefinitionSnapshot(
                                territories =
                                    listOf(
                                        MapTerritoryDefinitionSnapshot(
                                            territoryId = sourceId,
                                            edges = listOf(MapTerritoryEdgeSnapshot(targetId)),
                                        ),
                                        MapTerritoryDefinitionSnapshot(
                                            territoryId = targetId,
                                            edges = emptyList(),
                                        ),
                                    ),
                                continents = emptyList(),
                            ),
                        territoryStates =
                            listOf(
                                MapTerritoryStateSnapshot(sourceId, aliceId, 4),
                                MapTerritoryStateSnapshot(targetId, bobId, 1),
                            ),
                    ),
                players = players,
            )

        assertFalse(result.isCatchingUp)
        assertEquals(12, result.stateVersion)
        assertEquals(4, result.territoryStates.getValue(sourceId).troopCount)
        assertEquals(1, result.territoryStates.getValue(targetId).troopCount)
        assertEquals(2, result.attackState.attackTroops)
        assertEquals(2, result.attackState.moveAfterCapture)
        assertTrue(result.attackState.autoAttack.isEnabled)
        assertEquals(intent, result.attackState.autoAttack.intent)
        assertFalse(result.attackState.autoAttack.isAwaitingResult)
        assertNull(result.attackState.autoAttack.pendingRequestId)
        assertNull(result.attackState.autoAttack.errorText)
    }

    @Test
    fun `attack selection changes keep armed auto attack setting`() {
        val sourceId = TerritoryId("brasilien")
        val result =
            ClientGameStateReducer.selectRegion(
                current =
                    GameUiState(
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.ATTACK,
                        territoryStates =
                            mapOf(
                                sourceId to GameTerritoryUiState(sourceId, aliceId, 5),
                            ),
                        attackState =
                            AttackUiState(
                                autoAttack =
                                    AutoAttackUiState(
                                        isEnabled = true,
                                        statusText = "Auto-Angriff beendet.",
                                    ),
                            ),
                    ),
                regionId = "brazil",
                localPlayerId = aliceId,
            )

        assertEquals("brazil", result.selectionFromRegionId)
        assertTrue(result.attackState.autoAttack.isEnabled)
        assertFalse(result.attackState.autoAttack.isRunning)
        assertNull(result.attackState.autoAttack.statusText)
    }

    @Test
    fun `phase and snapshot resets keep armed auto attack toggle`() {
        val sourceId = TerritoryId("brasilien")
        val targetId = TerritoryId("argentinien")
        val current =
            GameUiState(
                stateVersion = 1,
                activePlayerId = aliceId,
                turnPhase = TurnPhase.ATTACK,
                attackState =
                    AttackUiState(
                        autoAttack =
                            AutoAttackUiState(
                                intent =
                                    AutoAttackIntent(
                                        fromTerritoryId = sourceId,
                                        toTerritoryId = targetId,
                                        attackTroops = 2,
                                        moveAfterCapture = 2,
                                    ),
                                isEnabled = true,
                                isAwaitingResult = true,
                                pendingRequestId = "auto-attack-1",
                            ),
                    ),
            )
        val phase =
            ClientGameStateReducer.applyPhaseBoundary(
                current = current,
                event =
                    PhaseBoundaryEvent(
                        lobbyCode = lobbyCode,
                        stateVersion = 2,
                        previousPhase = TurnPhase.ATTACK,
                        nextPhase = TurnPhase.FORTIFY,
                        activePlayerId = aliceId,
                        turnCount = 1,
                    ),
            )
        val turn =
            ClientGameStateReducer.applyTurnStateGetResponse(
                current = current,
                response =
                    TurnStateGetResponse(
                        lobbyCode = lobbyCode,
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.DRAW_CARD,
                        turnCount = 1,
                        startPlayerId = aliceId,
                    ),
            )
        val snapshot =
            ClientGameStateReducer.applyCatchUpResponse(
                current = current,
                response =
                    GameStateCatchUpResponse(
                        lobbyCode = lobbyCode,
                        stateVersion = 3,
                        determinism = determinism,
                        turnState =
                            PublicTurnStateSnapshot(
                                activePlayerId = aliceId,
                                turnPhase = TurnPhase.REINFORCEMENTS,
                                turnCount = 2,
                                startPlayerId = aliceId,
                                pendingReinforcements = 3,
                            ),
                        definition = mapDefinition("brasilien"),
                        territoryStates =
                            listOf(MapTerritoryStateSnapshot(sourceId, aliceId, 5)),
                    ),
                players = players,
            )

        listOf(phase, turn, snapshot).forEach { state ->
            assertTrue(state.attackState.autoAttack.isEnabled)
            assertFalse(state.attackState.autoAttack.isRunning)
            assertNull(state.attackState.autoAttack.pendingRequestId)
        }
    }

    @Test
    fun `map snapshot exposes adjacency for attack target validation`() {
        val state =
            ClientGameStateReducer.applyMapGetResponse(
                current = GameUiState(),
                response =
                    MapGetResponse(
                        lobbyCode = lobbyCode,
                        schemaVersion = 1,
                        mapHash = "hash",
                        stateVersion = 1,
                        definition =
                            MapDefinitionSnapshot(
                                territories =
                                    listOf(
                                        MapTerritoryDefinitionSnapshot(
                                            territoryId = TerritoryId("brasilien"),
                                            edges =
                                                listOf(
                                                    MapTerritoryEdgeSnapshot(
                                                        TerritoryId("argentinien"),
                                                    ),
                                                ),
                                        ),
                                    ),
                                continents = emptyList(),
                            ),
                        territoryStates = emptyList(),
                    ),
                players = players,
            )

        assertEquals(
            setOf(TerritoryId("argentinien")),
            state.adjacentTerritoryIds.getValue(TerritoryId("brasilien")),
        )
    }

    @Test
    fun `reinforcement events expose pending pool bonuses and bound placement amount`() {
        val initial =
            GameUiState(
                stateVersion = 1,
                activePlayerId = aliceId,
                turnPhase = TurnPhase.REINFORCEMENTS,
                selectedRegionId = "brazil",
            )
        val granted =
            ClientGameStateReducer.applyDelta(
                current = initial,
                delta =
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 1,
                        toVersion = 2,
                        events =
                            listOf(
                                ReinforcementsGrantedEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = aliceId,
                                    amount = 5,
                                    territoryBonus = 3,
                                    continentBonus = 2,
                                    cardBonus = 0,
                                ),
                            ),
                    ),
                players = players,
            ).state
        val increased =
            ClientGameStateReducer.adjustReinforcementPlacementAmount(
                current = granted,
                delta = 20,
            )
        val placed =
            ClientGameStateReducer.applyDelta(
                current = increased,
                delta =
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 2,
                        toVersion = 3,
                        events =
                            listOf(
                                PendingReinforcementsChangedEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = aliceId,
                                    delta = -5,
                                ),
                            ),
                    ),
                players = players,
            ).state

        assertEquals(5, granted.reinforcementState.pendingAmount)
        assertEquals(3, granted.reinforcementState.territoryBonus)
        assertTrue(granted.reinforcementState.isBonusBreakdownKnown)
        assertTrue(granted.canPlaceReinforcements(aliceId))
        assertEquals(5, increased.reinforcementPlacementAmount)
        assertEquals(0, placed.reinforcementState.pendingAmount)
        assertEquals(1, placed.reinforcementPlacementAmount)
        assertTrue(placed.canConfirmReinforcementsDone(aliceId))
        assertFalse(placed.canRequestTurnAdvance(aliceId))
    }

    @Test
    fun `trade grant adds card bonus without resetting existing pending pool`() {
        val state =
            GameUiState(
                stateVersion = 1,
                reinforcementState =
                    ReinforcementUiState(
                        playerId = aliceId,
                        pendingAmount = 3,
                        territoryBonus = 3,
                        isBonusBreakdownKnown = true,
                    ),
                turnPhase = TurnPhase.REINFORCEMENTS,
            )
        val grant =
            ClientGameStateReducer.applyDelta(
                current = state,
                delta =
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 1,
                        toVersion = 2,
                        events =
                            listOf(
                                ReinforcementsGrantedEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = aliceId,
                                    amount = 2,
                                    territoryBonus = 0,
                                    continentBonus = 0,
                                    cardBonus = 2,
                                ),
                            ),
                    ),
                players = players,
            ).state
        val increased =
            ClientGameStateReducer.applyDelta(
                current = grant,
                delta =
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 2,
                        toVersion = 3,
                        events =
                            listOf(
                                PendingReinforcementsChangedEvent(lobbyCode, aliceId, 2),
                            ),
                    ),
                players = players,
            ).state

        assertEquals(3, grant.reinforcementState.pendingAmount)
        assertEquals(2, grant.reinforcementState.cardBonus)
        assertTrue(grant.reinforcementState.isBonusBreakdownKnown)
        assertEquals(5, increased.reinforcementState.pendingAmount)
    }

    @Test
    fun `base reinforcement grant after zero snapshot initializes a new visible pool`() {
        val snapshotted =
            ClientGameStateReducer.applyCatchUpResponse(
                current = GameUiState(isCatchingUp = true),
                response =
                    GameStateCatchUpResponse(
                        lobbyCode = lobbyCode,
                        stateVersion = 1,
                        determinism = determinism,
                        turnState =
                            PublicTurnStateSnapshot(
                                activePlayerId = aliceId,
                                turnPhase = TurnPhase.REINFORCEMENTS,
                                turnCount = 1,
                                startPlayerId = aliceId,
                                pendingReinforcements = 0,
                            ),
                        definition = mapDefinition("brasilien"),
                        territoryStates = emptyList(),
                    ),
                players = players,
            )

        val granted =
            ClientGameStateReducer.applyDelta(
                current = snapshotted,
                delta =
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 1,
                        toVersion = 2,
                        events =
                            listOf(
                                ReinforcementsGrantedEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = aliceId,
                                    amount = 3,
                                    territoryBonus = 3,
                                    continentBonus = 0,
                                    cardBonus = 0,
                                ),
                            ),
                    ),
                players = players,
            ).state

        assertEquals(3, granted.reinforcementState.pendingAmount)
        assertEquals(3, granted.reinforcementState.territoryBonus)
        assertTrue(granted.reinforcementState.isBonusBreakdownKnown)
    }

    @Test
    fun `private typed hand supports selecting three cards and clears removed cards`() {
        val cards =
            listOf(
                PrivateHandCardSnapshot(CardId("a"), CardType.A),
                PrivateHandCardSnapshot(CardId("b"), CardType.B),
                PrivateHandCardSnapshot(CardId("c"), CardType.C),
            )
        val private =
            ClientGameStateReducer.applyPrivateGetResponse(
                current =
                    GameUiState(
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                    ),
                response =
                    GameStatePrivateGetResponse(
                        lobbyCode = lobbyCode,
                        recipientPlayerId = aliceId,
                        stateVersion = 1,
                        privateHandCards = cards,
                    ),
            )
        val selected =
            cards.fold(private) { current, card ->
                ClientGameStateReducer.toggleTradeInCard(current, card.cardId)
            }
        val updated =
            ClientGameStateReducer.applyPlayerHandUpdatedEvent(
                current = selected,
                event =
                    PlayerHandUpdatedEvent(
                        lobbyCode = lobbyCode,
                        recipientPlayerId = aliceId,
                        stateVersion = 2,
                        handCards = listOf(cards.first()),
                    ),
            )

        assertEquals(3, selected.privateHandCards.size)
        assertTrue(selected.canTradeInCards(aliceId))
        assertEquals(setOf(CardId("a")), updated.selectedTradeInCardIds)
        assertEquals(1, updated.privateHandCards.size)
        assertFalse(
            ClientGameStateReducer
                .toggleTradeInCard(updated, CardId("missing"))
                .selectedTradeInCardIds
                .contains(CardId("missing")),
        )
    }

    @Test
    fun `reinforcement phase updates retain local pool and guard card selection`() {
        val cards =
            listOf(
                PrivateHandCardUi(CardId("a"), CardType.A),
                PrivateHandCardUi(CardId("b"), CardType.B),
                PrivateHandCardUi(CardId("c"), CardType.C),
                PrivateHandCardUi(CardId("d"), CardType.JOKER),
            )
        val initial =
            GameUiState(
                stateVersion = 1,
                activePlayerId = aliceId,
                turnPhase = TurnPhase.REINFORCEMENTS,
                reinforcementState = ReinforcementUiState(aliceId, pendingAmount = 4),
                privateHandCards = cards,
                selectedTradeInCardIds = setOf(CardId("a"), CardId("b"), CardId("c")),
            )

        val boundary =
            ClientGameStateReducer.applyPhaseBoundary(
                initial,
                PhaseBoundaryEvent(
                    lobbyCode = lobbyCode,
                    stateVersion = 2,
                    previousPhase = TurnPhase.ATTACK,
                    nextPhase = TurnPhase.REINFORCEMENTS,
                    activePlayerId = aliceId,
                    turnCount = 2,
                ),
            )
        val turn =
            ClientGameStateReducer.applyTurnStateGetResponse(
                initial,
                TurnStateGetResponse(
                    lobbyCode = lobbyCode,
                    activePlayerId = aliceId,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    turnCount = 2,
                    startPlayerId = aliceId,
                ),
            )
        val publicUpdate =
            ClientGameStateReducer.applyDelta(
                current = initial,
                delta =
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 1,
                        toVersion = 2,
                        events =
                            listOf(
                                TurnStateUpdatedEvent(
                                    lobbyCode = lobbyCode,
                                    activePlayerId = aliceId,
                                    turnPhase = TurnPhase.REINFORCEMENTS,
                                    turnCount = 2,
                                    startPlayerId = aliceId,
                                ),
                            ),
                    ),
                players = players,
            ).state
        val deselected = ClientGameStateReducer.toggleTradeInCard(initial, CardId("a"))
        val capped = ClientGameStateReducer.toggleTradeInCard(initial, CardId("d"))
        val unrelatedPendingEvent =
            ClientGameStateReducer.applyDelta(
                current = initial,
                delta =
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 1,
                        toVersion = 2,
                        events =
                            listOf(
                                PendingReinforcementsChangedEvent(
                                    lobbyCode = lobbyCode,
                                    playerId = bobId,
                                    delta = -1,
                                ),
                            ),
                    ),
                players = players,
            ).state

        assertEquals(initial.reinforcementState, boundary.reinforcementState)
        assertEquals(initial.selectedTradeInCardIds, boundary.selectedTradeInCardIds)
        assertEquals(initial.reinforcementState, turn.reinforcementState)
        assertEquals(initial.selectedTradeInCardIds, turn.selectedTradeInCardIds)
        assertEquals(initial.reinforcementState, publicUpdate.reinforcementState)
        assertEquals(initial.selectedTradeInCardIds, publicUpdate.selectedTradeInCardIds)
        assertEquals(setOf(CardId("b"), CardId("c")), deselected.selectedTradeInCardIds)
        assertEquals(initial, capped)
        assertEquals(initial.reinforcementState, unrelatedPendingEvent.reinforcementState)
    }

    @Test
    fun `region selection toggles source target and card visibility`() {
        val sourceSelected =
            ClientGameStateReducer.selectRegion(
                current = GameUiState(),
                regionId = "brazil",
                localPlayerId = aliceId,
            )
        val sourceCleared =
            ClientGameStateReducer.selectRegion(
                current = sourceSelected,
                regionId = "brazil",
                localPlayerId = aliceId,
            )
        val targetSelected =
            ClientGameStateReducer.selectRegion(
                current = sourceSelected,
                regionId = "argentina",
                localPlayerId = aliceId,
            )

        assertEquals("brazil", sourceSelected.selectionFromRegionId)
        assertEquals(null, sourceCleared.selectedRegionId)
        assertEquals("argentina", targetSelected.selectionToRegionId)
        assertTrue(ClientGameStateReducer.toggleCards(GameUiState()).cardsVisible)
    }

    @Test
    fun `phase turn and private responses update only their local slices`() {
        val selected =
            GameUiState(
                stateVersion = 3,
                selectedRegionId = "brazil",
                selectionFromRegionId = "brazil",
                selectionToRegionId = "argentina",
                selectionMessage = "local",
                isCatchingUp = true,
                isDesynced = true,
                lastSyncError = "old",
            )
        val stalePhase =
            ClientGameStateReducer.applyPhaseBoundary(
                current = selected,
                event =
                    PhaseBoundaryEvent(
                        lobbyCode = lobbyCode,
                        stateVersion = 2,
                        previousPhase = TurnPhase.REINFORCEMENTS,
                        nextPhase = TurnPhase.ATTACK,
                        activePlayerId = aliceId,
                        turnCount = 1,
                    ),
            )
        val phase =
            ClientGameStateReducer.applyPhaseBoundary(
                current = selected,
                event =
                    PhaseBoundaryEvent(
                        lobbyCode = lobbyCode,
                        stateVersion = 4,
                        previousPhase = TurnPhase.REINFORCEMENTS,
                        nextPhase = TurnPhase.ATTACK,
                        activePlayerId = bobId,
                        turnCount = 2,
                    ),
            )
        val turn =
            ClientGameStateReducer.applyTurnStateGetResponse(
                current = GameUiState(isCatchingUp = true, lastSyncError = "old"),
                response =
                    TurnStateGetResponse(
                        lobbyCode = lobbyCode,
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.DRAW_CARD,
                        turnCount = 3,
                        startPlayerId = aliceId,
                        isPaused = true,
                        pauseReason = "waiting",
                        pausedPlayerId = aliceId,
                    ),
            )
        val turnPhaseRefresh =
            ClientGameStateReducer.applyTurnStateGetResponse(
                current =
                    GameUiState(
                        turnPhase = TurnPhase.ATTACK,
                        selectedRegionId = "argentina",
                        selectionFromRegionId = "brazil",
                        selectionToRegionId = "argentina",
                        selectionMessage = "Zielgebiet ausgewählt.",
                    ),
                response =
                    TurnStateGetResponse(
                        lobbyCode = lobbyCode,
                        activePlayerId = aliceId,
                        turnPhase = TurnPhase.DRAW_CARD,
                        turnCount = 3,
                        startPlayerId = aliceId,
                    ),
            )
        val private =
            ClientGameStateReducer.applyPrivateGetResponse(
                current = GameUiState(lastSyncError = "old"),
                response =
                    GameStatePrivateGetResponse(
                        lobbyCode = lobbyCode,
                        recipientPlayerId = aliceId,
                        stateVersion = 4,
                        handCards = listOf("card-a"),
                        secretObjectives = listOf("objective-a"),
                    ),
            )

        assertEquals(selected, stalePhase)
        assertEquals(4, phase.stateVersion)
        assertEquals(null, phase.selectedRegionId)
        assertEquals(bobId, phase.activePlayerId)
        assertEquals(TurnPhase.DRAW_CARD, turn.turnPhase)
        assertTrue(turn.isPaused)
        assertEquals(null, turnPhaseRefresh.selectedRegionId)
        assertEquals(null, turnPhaseRefresh.selectionFromRegionId)
        assertEquals(null, turnPhaseRefresh.selectionToRegionId)
        assertEquals(null, turnPhaseRefresh.selectionMessage)
        assertEquals(listOf("card-a"), private.handCards)
        assertEquals(listOf("objective-a"), private.secretObjectives)
        assertEquals(null, private.lastSyncError)
    }

    @Test
    fun `player list arriving after snapshot should rebuild region owners`() {
        val stateWithoutPlayers =
            GameUiState(
                territoryStates =
                    mapOf(
                        TerritoryId("brasilien") to
                            GameTerritoryUiState(
                                territoryId = TerritoryId("brasilien"),
                                ownerId = aliceId,
                                troopCount = 5,
                            ),
                    ),
            ).let { state ->
                state.copy(
                    regionStates =
                        buildRegionStates(
                            territoryStates = state.territoryStates,
                            players = emptyList(),
                        ),
                )
            }

        val restoredState =
            ClientGameStateReducer.applyPlayers(
                current = stateWithoutPlayers,
                players = players,
            )

        assertEquals(
            "Verlassener Spieler",
            stateWithoutPlayers.regionStates.getValue("brazil").ownerName,
        )
        assertEquals("Alice", restoredState.regionStates.getValue("brazil").ownerName)
        assertEquals("1", restoredState.regionStates.getValue("brazil").ownerPlayerId)
    }

    /**
     * Erstellt eine schlanke Map-Definition für Reducer-Tests.
     *
     * @param territoryIds Backend-Territory-IDs, die im Snapshot enthalten sein sollen.
     */
    private fun mapDefinition(vararg territoryIds: String): MapDefinitionSnapshot =
        MapDefinitionSnapshot(
            territories =
                territoryIds.map { territoryId ->
                    MapTerritoryDefinitionSnapshot(
                        territoryId = TerritoryId(territoryId),
                        edges = emptyList(),
                    )
                },
            continents = emptyList(),
        )

    private companion object {
        val lobbyCode = LobbyCode("T123")
        val aliceId = PlayerId(1)
        val bobId = PlayerId(2)
        val players =
            listOf(
                LobbyPlayerUi(playerId = aliceId, displayName = "Alice", isHost = true),
                LobbyPlayerUi(playerId = bobId, displayName = "Bob"),
            )
        val determinism =
            PublicDeterminismMetadataSnapshot(
                mapHash = "hash",
                schemaVersion = 1,
            )
    }
}
