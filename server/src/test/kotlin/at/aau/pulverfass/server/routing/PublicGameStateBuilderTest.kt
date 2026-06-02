package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsSetEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.StartPlayerConfigured
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.PendingReinforcements
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateGameEvent
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.response.PublicGameStateSnapshot
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class PublicGameStateBuilderTest {
    private val builder = PublicGameStateBuilder()
    private val json =
        Json {
            explicitNulls = false
        }

    @Test
    fun `snapshot builder exposes all required public fields consistently`() {
        val gameState =
            sampleGameState().copy(
                stateVersion = 9,
                activePlayer = PlayerId(2),
                turnState =
                    TurnState(
                        activePlayerId = PlayerId(2),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 2,
                        startPlayerId = PlayerId(1),
                    ),
                pendingReinforcements = PendingReinforcements(PlayerId(2), 4),
            )

        val snapshot = builder.buildSnapshot(gameState)
        val mapGet = builder.buildMapGetResponse(gameState)
        val catchUp = builder.buildCatchUpResponse(gameState)
        val broadcast = builder.buildSnapshotBroadcast(gameState)

        assertEquals(gameState.lobbyCode, snapshot.lobbyCode)
        assertEquals(9, snapshot.stateVersion)
        assertEquals(gameState.mapDefinition?.mapHash, snapshot.determinism.mapHash)
        assertEquals(gameState.mapDefinition?.schemaVersion, snapshot.determinism.schemaVersion)
        assertEquals(null, snapshot.determinism.seed)
        assertNotNull(snapshot.turnState)
        assertEquals(4, snapshot.turnState.pendingReinforcements)
        assertEquals(gameState.allTerritoryStates().size, snapshot.territoryStates.size)

        assertEquals(snapshot.lobbyCode, mapGet.lobbyCode)
        assertEquals(snapshot.stateVersion, mapGet.stateVersion)
        assertEquals(snapshot.definition, mapGet.definition)
        assertEquals(snapshot.territoryStates, mapGet.territoryStates)

        assertEquals(snapshot.lobbyCode, catchUp.lobbyCode)
        assertEquals(snapshot.stateVersion, catchUp.stateVersion)
        assertEquals(snapshot.determinism, catchUp.determinism)
        assertEquals(snapshot.turnState, catchUp.turnState)

        assertEquals(snapshot.lobbyCode, broadcast.lobbyCode)
        assertEquals(snapshot.stateVersion, broadcast.stateVersion)
        assertEquals(snapshot.determinism, broadcast.determinism)
        assertEquals(snapshot.turnState, broadcast.turnState)
    }

    @Test
    fun `snapshot builder exposes consumed fortify move in public turn state`() {
        val gameState =
            sampleGameState().copy(
                stateVersion = 10,
                activePlayer = PlayerId(1),
                fortifyUsedThisTurn = true,
                turnState =
                    TurnState(
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.FORTIFY,
                        turnCount = 3,
                        startPlayerId = PlayerId(1),
                    ),
            )

        val snapshot = builder.buildSnapshot(gameState)
        val catchUp = builder.buildCatchUpResponse(gameState)
        val broadcast = builder.buildSnapshotBroadcast(gameState)

        assertTrue(snapshot.turnState.fortifyUsedThisTurn)
        assertTrue(catchUp.turnState.fortifyUsedThisTurn)
        assertTrue(broadcast.turnState.fortifyUsedThisTurn)
    }

    @Test
    fun `snapshot builder excludes private fields`() {
        val snapshot = builder.buildSnapshot(sampleGameState())

        val serialized = json.encodeToString(PublicGameStateSnapshot.serializer(), snapshot)

        assertFalse(serialized.contains("recipientPlayerId"))
        assertFalse(serialized.contains("handCards"))
        assertFalse(serialized.contains("secretObjectives"))
        assertFalse(serialized.contains("\"seed\":"))
    }

    @Test
    fun `delta builder rejects non public payloads`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                builder.buildDelta(
                    lobbyCode = LobbyCode("PGB1"),
                    fromVersion = 4,
                    toVersion = 4,
                    payloads =
                        listOf(
                            FakePublicEvent("ok"),
                            FakePrivateEvent(PlayerId(2), "secret"),
                        ),
                )
            }

        assertEquals(
            "GameStateDeltaEvent darf nur PublicGameEvent enthalten. " +
                "Nicht-oeffentliche Payloads: FakePrivateEvent.",
            exception.message,
        )
    }

    @Test
    fun `pending reinforcement set projects to public reinforcement grant event`() {
        val previousState =
            sampleGameState().copy(
                activePlayer = PlayerId(2),
                turnState =
                    TurnState(
                        activePlayerId = PlayerId(2),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                    ),
            )
        val currentState =
            previousState.copy(
                pendingReinforcements = PendingReinforcements(PlayerId(2), 3),
                stateVersion = 5,
            )

        val delta =
            builder.buildDelta(
                lobbyCode = previousState.lobbyCode,
                event = PendingReinforcementsSetEvent(previousState.lobbyCode, PlayerId(2), 3),
                previousState = previousState,
                currentState = currentState,
            )

        assertEquals(
            listOf(
                ReinforcementsGrantedEvent(
                    lobbyCode = previousState.lobbyCode,
                    playerId = PlayerId(2),
                    amount = 3,
                    territoryBonus = 3,
                    continentBonus = 0,
                    cardBonus = 0,
                ),
            ),
            delta?.events,
        )
    }

    @Test
    fun `card trade in projects to public reinforcement grant with card bonus only`() {
        val previousState = sampleGameState()
        val currentState = previousState.copy(stateVersion = 1, tradedInSetCount = 1)

        val delta =
            builder.buildDelta(
                lobbyCode = previousState.lobbyCode,
                event =
                    CardSetTradedInEvent(
                        lobbyCode = previousState.lobbyCode,
                        playerId = PlayerId(2),
                        cardIds = listOf(CardId("card-a"), CardId("card-b"), CardId("card-c")),
                        value = 2,
                        tradeIndex = 1,
                    ),
                previousState = previousState,
                currentState = currentState,
            )

        assertEquals(
            listOf(
                ReinforcementsGrantedEvent(
                    lobbyCode = previousState.lobbyCode,
                    playerId = PlayerId(2),
                    amount = 2,
                    territoryBonus = 0,
                    continentBonus = 0,
                    cardBonus = 2,
                ),
            ),
            delta?.events,
        )
    }

    @Test
    fun `attack resolved event projects to visible territory delta`() {
        val previousState = sampleGameState()
        val currentState = previousState.copy(stateVersion = 1)

        val delta =
            builder.buildDelta(
                lobbyCode = previousState.lobbyCode,
                event =
                    AttackResolvedEvent(
                        lobbyCode = previousState.lobbyCode,
                        attackerPlayerId = PlayerId(1),
                        defenderPlayerId = PlayerId(2),
                        fromTerritoryId = TerritoryId("territory-1"),
                        toTerritoryId = TerritoryId("territory-2"),
                        attackTroops = 3,
                        sourceTroopsBefore = 5,
                        targetTroopsBefore = 2,
                        requestedAttackDice = 3,
                        attackDice = 3,
                        defendDice = 2,
                        attackerRolls = listOf(5, 4, 3),
                        defenderRolls = listOf(2, 1),
                        rngTrace = listOf(5, 3, 4, 1, 2),
                        rngStateBefore = 2L,
                        rngStateAfter = 3L,
                        attackerLosses = 0,
                        defenderLosses = 2,
                        attackerRemaining = 5,
                        defenderRemaining = 0,
                        occupyingTroopCount = 3,
                        minOccupyingTroops = 3,
                    ),
                previousState = previousState,
                currentState = currentState,
            )

        assertEquals(
            listOf(
                AttackResolvedBroadcastEvent(
                    lobbyCode = previousState.lobbyCode,
                    attackerPlayerId = PlayerId(1),
                    defenderPlayerId = PlayerId(2),
                    fromTerritoryId = TerritoryId("territory-1"),
                    toTerritoryId = TerritoryId("territory-2"),
                    attackTroops = 3,
                    sourceTroopsBefore = 5,
                    targetTroopsBefore = 2,
                    requestedAttackDice = 3,
                    attackDice = 3,
                    defendDice = 2,
                    attackerRolls = listOf(5, 4, 3),
                    defenderRolls = listOf(2, 1),
                    attackerLosses = 0,
                    defenderLosses = 2,
                    attackerRemaining = 5,
                    defenderRemaining = 0,
                    occupyingTroopCount = 3,
                    minOccupyingTroops = 3,
                    stateVersion = 1L,
                ),
                TerritoryTroopsChangedEvent(
                    previousState.lobbyCode,
                    TerritoryId("territory-1"),
                    2,
                    1L,
                ),
                TerritoryOwnerChangedEvent(
                    previousState.lobbyCode,
                    TerritoryId("territory-2"),
                    PlayerId(1),
                    1L,
                ),
                TerritoryTroopsChangedEvent(
                    previousState.lobbyCode,
                    TerritoryId("territory-2"),
                    3,
                    1L,
                ),
            ),
            delta?.events,
        )
    }

    @Test
    fun `player eliminated event projects to public elimination event`() {
        val previousState = sampleGameState()
        val currentState =
            previousState.copy(
                stateVersion = 2,
                turnOrder = listOf(PlayerId(1)),
            )

        val delta =
            builder.buildDelta(
                lobbyCode = previousState.lobbyCode,
                event =
                    PlayerEliminatedEvent(
                        lobbyCode = previousState.lobbyCode,
                        playerId = PlayerId(2),
                        eliminatedByPlayerId = PlayerId(1),
                    ),
                previousState = previousState,
                currentState = currentState,
            )

        assertEquals(
            listOf(
                PlayerEliminatedEvent(
                    lobbyCode = previousState.lobbyCode,
                    playerId = PlayerId(2),
                    eliminatedByPlayerId = PlayerId(1),
                    stateVersion = 2L,
                ),
            ),
            delta?.events,
        )
    }

    @Test
    fun `player eliminated event also projects turn state change`() {
        val previousState =
            sampleGameState().copy(
                activePlayer = PlayerId(2),
                configuredStartPlayerId = PlayerId(2),
                turnState =
                    TurnState(
                        activePlayerId = PlayerId(2),
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 3,
                        startPlayerId = PlayerId(2),
                    ),
            )
        val currentState =
            previousState.copy(
                stateVersion = 2,
                activePlayer = PlayerId(1),
                configuredStartPlayerId = PlayerId(1),
                turnOrder = listOf(PlayerId(1)),
                turnState =
                    TurnState(
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 3,
                        startPlayerId = PlayerId(1),
                    ),
            )

        val delta =
            builder.buildDelta(
                lobbyCode = previousState.lobbyCode,
                event =
                    PlayerEliminatedEvent(
                        lobbyCode = previousState.lobbyCode,
                        playerId = PlayerId(2),
                        eliminatedByPlayerId = PlayerId(1),
                    ),
                previousState = previousState,
                currentState = currentState,
            )

        assertEquals(
            listOf(
                PlayerEliminatedEvent(
                    lobbyCode = previousState.lobbyCode,
                    playerId = PlayerId(2),
                    eliminatedByPlayerId = PlayerId(1),
                    stateVersion = 2L,
                ),
                TurnStateUpdatedEvent(
                    lobbyCode = previousState.lobbyCode,
                    activePlayerId = PlayerId(1),
                    turnPhase = TurnPhase.ATTACK,
                    turnCount = 3,
                    startPlayerId = PlayerId(1),
                ),
            ),
            delta?.events,
        )
    }

    @Test
    fun `game started projects game started event and current turn state`() {
        val previousState = sampleGameState().copy(gameStarted = false)
        val currentState =
            previousState.copy(
                gameStarted = true,
                stateVersion = 4,
                turnState =
                    TurnState(
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                    ),
            )

        val delta =
            builder.buildDelta(
                lobbyCode = previousState.lobbyCode,
                event = GameStarted(previousState.lobbyCode, 42L),
                previousState = previousState,
                currentState = currentState,
            )

        assertEquals(
            listOf(
                at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent(
                    previousState.lobbyCode,
                ),
                TurnStateUpdatedEvent(
                    lobbyCode = previousState.lobbyCode,
                    activePlayerId = PlayerId(1),
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    turnCount = 1,
                    startPlayerId = PlayerId(1),
                ),
            ),
            delta?.events,
        )
    }

    @Test
    fun `start player configured projects updated turn state only`() {
        val previousState = sampleGameState()
        val currentState =
            previousState.copy(
                stateVersion = 3,
                activePlayer = PlayerId(2),
                configuredStartPlayerId = PlayerId(2),
                turnState =
                    TurnState(
                        activePlayerId = PlayerId(2),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(2),
                    ),
            )

        val delta =
            builder.buildDelta(
                lobbyCode = previousState.lobbyCode,
                event =
                    StartPlayerConfigured(
                        lobbyCode = previousState.lobbyCode,
                        startPlayerId = PlayerId(2),
                        requesterPlayerId = PlayerId(1),
                    ),
                previousState = previousState,
                currentState = currentState,
            )

        assertEquals(
            listOf(
                TurnStateUpdatedEvent(
                    lobbyCode = previousState.lobbyCode,
                    activePlayerId = PlayerId(2),
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    turnCount = 1,
                    startPlayerId = PlayerId(2),
                ),
            ),
            delta?.events,
        )
    }

    @Test
    fun `buildSnapshot throws when game state has no turn state`() {
        val gameState =
            GameState.initial(
                lobbyCode = LobbyCode("PGB2"),
                mapDefinition = at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault(),
            ).copy(
                turnState = null,
                activePlayer = null,
                turnOrder = emptyList(),
                turnNumber = 0,
            )

        val exception =
            assertThrows(IllegalStateException::class.java) {
                builder.buildSnapshot(gameState)
            }
        assertEquals(
            "GameState enthält keinen TurnState für einen Snapshot.",
            exception.message,
        )
    }

    @Test
    fun `buildSnapshot throws when game state has no map definition`() {
        val gameState =
            GameState(
                lobbyCode = LobbyCode("PGB3"),
                players = listOf(PlayerId(1)),
                playerDisplayNames = mapOf(PlayerId(1) to "One"),
                activePlayer = PlayerId(1),
                configuredStartPlayerId = PlayerId(1),
                turnOrder = listOf(PlayerId(1)),
                turnNumber = 1,
                turnState =
                    TurnState(
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                    ),
            )

        val exception =
            assertThrows(IllegalStateException::class.java) {
                builder.buildSnapshot(gameState)
            }

        assertEquals(
            "GameState enthält keine MapDefinition für einen Snapshot.",
            exception.message,
        )
    }

    @Test
    fun `fortify move event projects two troop updates into a public delta`() {
        val reducer = DefaultLobbyEventReducer()
        val playerOne = PlayerId(1)
        val lobbyCode = LobbyCode("PGB2")
        val baseState =
            sampleGameState().copy(
                lobbyCode = lobbyCode,
                activePlayer = playerOne,
            )
        val ownedState =
            reducer.apply(
                reducer.apply(
                    reducer.apply(
                        reducer.apply(
                            baseState,
                            TerritoryOwnerChangedEvent(
                                lobbyCode,
                                TerritoryId("alaska"),
                                playerOne,
                            ),
                        ),
                        TerritoryOwnerChangedEvent(
                            lobbyCode,
                            TerritoryId("kanada"),
                            playerOne,
                        ),
                    ),
                    TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("alaska"), 4),
                ),
                TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("kanada"), 1),
            )
        val fortifyEvent =
            FortifyMoveAppliedEvent(
                lobbyCode = lobbyCode,
                playerId = playerOne,
                fromTerritoryId = TerritoryId("alaska"),
                toTerritoryId = TerritoryId("kanada"),
                troopCount = 2,
            )
        val currentState = reducer.apply(ownedState, fortifyEvent)

        assertEquals(
            GameStateDeltaEvent(
                lobbyCode = lobbyCode,
                fromVersion = currentState.stateVersion,
                toVersion = currentState.stateVersion,
                events =
                    listOf(
                        TerritoryTroopsChangedEvent(
                            lobbyCode = lobbyCode,
                            territoryId = TerritoryId("alaska"),
                            troopCount = 2,
                            stateVersion = currentState.stateVersion,
                        ),
                        TerritoryTroopsChangedEvent(
                            lobbyCode = lobbyCode,
                            territoryId = TerritoryId("kanada"),
                            troopCount = 3,
                            stateVersion = currentState.stateVersion,
                        ),
                    ),
            ),
            builder.buildDelta(
                lobbyCode = lobbyCode,
                event = fortifyEvent,
                previousState = ownedState,
                currentState = currentState,
            ),
        )
    }

    @Test
    fun `fortify used flag does not create a public delta`() {
        val state = sampleGameState()

        assertNull(
            builder.buildDelta(
                lobbyCode = state.lobbyCode,
                event = FortifyUsedSetEvent(state.lobbyCode, used = true),
                previousState = state,
                currentState = state.copy(fortifyUsedThisTurn = true, stateVersion = 1),
            ),
        )
    }

    private fun sampleGameState(): GameState =
        GameState.initial(
            lobbyCode = LobbyCode("PGB0"),
            mapDefinition = at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault(),
            players = listOf(PlayerId(1), PlayerId(2)),
            playerDisplayNames =
                mapOf(
                    PlayerId(1) to "One",
                    PlayerId(2) to "Two",
                ),
        )

    private data class FakePublicEvent(
        val marker: String,
    ) : PublicGameEvent

    private data class FakePrivateEvent(
        override val recipientPlayerId: PlayerId,
        val secret: String,
    ) : PrivateGameEvent
}
