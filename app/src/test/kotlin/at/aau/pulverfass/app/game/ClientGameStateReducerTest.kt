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
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        assertEquals("Neutral", stateWithoutPlayers.regionStates.getValue("brazil").ownerName)
        assertEquals("Alice", restoredState.regionStates.getValue("brazil").ownerName)
        assertEquals("1", restoredState.regionStates.getValue("brazil").ownerPlayerId)
    }

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
