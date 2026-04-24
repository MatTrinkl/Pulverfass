package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.event.PrivateGameEvent
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.lobby.response.PublicGameStateSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
