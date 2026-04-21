package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpReason
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameStateCatchUpMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request =
            GameStateCatchUpRequest(
                lobbyCode = LobbyCode("CU12"),
                clientStateVersion = 5,
                reason = GameStateCatchUpReason.MISSING_DELTA,
            )

        val serialized = json.encodeToString(GameStateCatchUpRequest.serializer(), request)
        val deserialized = json.decodeFromString(GameStateCatchUpRequest.serializer(), serialized)

        assertTrue(serialized.contains("clientStateVersion"))
        assertTrue(serialized.contains("MISSING_DELTA"))
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response =
            GameStateCatchUpResponse(
                lobbyCode = LobbyCode("CU34"),
                stateVersion = 9,
                determinism =
                    PublicDeterminismMetadataSnapshot(
                        mapHash = "hash",
                        schemaVersion = 1,
                    ),
                turnState =
                    PublicTurnStateSnapshot(
                        activePlayerId = PlayerId(2),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 2,
                        startPlayerId = PlayerId(1),
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
                            ownerId = PlayerId(1),
                            troopCount = 3,
                        ),
                    ),
            )

        val serialized = json.encodeToString(GameStateCatchUpResponse.serializer(), response)
        val deserialized = json.decodeFromString(GameStateCatchUpResponse.serializer(), serialized)

        assertTrue(serialized.contains("determinism"))
        assertTrue(serialized.contains("turnState"))
        assertFalse(serialized.contains("recipientPlayerId"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            GameStateCatchUpErrorResponse(
                code = GameStateCatchUpErrorCode.NOT_IN_GAME,
                reason = "Spieler '7' ist nicht Teil von Lobby 'CU56'.",
            )

        val serialized = json.encodeToString(GameStateCatchUpErrorResponse.serializer(), response)
        val deserialized = json.decodeFromString(GameStateCatchUpErrorResponse.serializer(), serialized)

        assertEquals(response, deserialized)
    }
}
