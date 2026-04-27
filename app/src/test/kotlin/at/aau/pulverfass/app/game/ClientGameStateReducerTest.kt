package at.aau.pulverfass.app.game

import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
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
        assertEquals(5, state.regionStates.getValue("brazil").troopCount)
        assertEquals("Alice", state.regionStates.getValue("brazil").ownerName)
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
                fromVersion = 2,
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
