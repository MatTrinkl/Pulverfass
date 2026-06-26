package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameStateSnapshotBroadcastMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip game state snapshot broadcast`() {
        val payload =
            GameStateSnapshotBroadcast(
                lobbyCode = LobbyCode("AB12"),
                stateVersion = 14,
                determinism =
                    PublicDeterminismMetadataSnapshot(
                        mapHash = "abc123",
                        schemaVersion = 1,
                    ),
                turnState =
                    PublicTurnStateSnapshot(
                        activePlayerId = PlayerId(7),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 4,
                        startPlayerId = PlayerId(2),
                    ),
                definition =
                    MapDefinitionSnapshot(
                        territories =
                            listOf(
                                MapTerritoryDefinitionSnapshot(
                                    territoryId = TerritoryId("alpha"),
                                    edges = listOf(MapTerritoryEdgeSnapshot(TerritoryId("beta"))),
                                ),
                            ),
                        continents = emptyList(),
                    ),
                territoryStates =
                    listOf(
                        MapTerritoryStateSnapshot(
                            territoryId = TerritoryId("alpha"),
                            ownerId = PlayerId(7),
                            troopCount = 5,
                        ),
                    ),
            )

        val serialized = json.encodeToString(GameStateSnapshotBroadcast.serializer(), payload)
        val deserialized =
            json.decodeFromString(
                GameStateSnapshotBroadcast.serializer(),
                serialized,
            )

        assertTrue(serialized.contains("determinism"))
        assertTrue(serialized.contains("turnState"))
        assertTrue(serialized.contains("territoryStates"))
        assertFalse(serialized.contains("recipientPlayerId"))
        assertEquals(payload, deserialized)
    }
}
