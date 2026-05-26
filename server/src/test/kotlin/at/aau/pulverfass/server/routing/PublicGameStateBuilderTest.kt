package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateGameEvent
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.lobby.response.PublicGameStateSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PublicGameStateBuilderTest {
    private val builder = PublicGameStateBuilder()
    private val json = Json

    @Test
    fun `snapshot builder exposes all required public fields consistently`() {
        val gameState = sampleGameState().copy(stateVersion = 9)

        val snapshot = builder.buildSnapshot(gameState)
        val mapGet = builder.buildMapGetResponse(gameState)
        val catchUp = builder.buildCatchUpResponse(gameState)
        val broadcast = builder.buildSnapshotBroadcast(gameState)

        assertEquals(gameState.lobbyCode, snapshot.lobbyCode)
        assertEquals(9, snapshot.stateVersion)
        assertEquals(gameState.mapDefinition?.mapHash, snapshot.determinism.mapHash)
        assertEquals(gameState.mapDefinition?.schemaVersion, snapshot.determinism.schemaVersion)
        assertNotNull(snapshot.turnState)
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
    fun `snapshot builder excludes private fields`() {
        val snapshot = builder.buildSnapshot(sampleGameState())

        val serialized = json.encodeToString(PublicGameStateSnapshot.serializer(), snapshot)

        assertFalse(serialized.contains("recipientPlayerId"))
        assertFalse(serialized.contains("handCards"))
        assertFalse(serialized.contains("secretObjectives"))
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
