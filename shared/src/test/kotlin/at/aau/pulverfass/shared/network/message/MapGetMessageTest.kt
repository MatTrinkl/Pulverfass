package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.response.MapContinentDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapGetMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request = MapGetRequest(lobbyCode = LobbyCode("AB12"))

        val serialized = json.encodeToString(MapGetRequest.serializer(), request)
        val deserialized = json.decodeFromString(MapGetRequest.serializer(), serialized)

        assertEquals("""{"lobbyCode":"AB12"}""", serialized)
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response = sampleResponse()

        val serialized = json.encodeToString(MapGetResponse.serializer(), response)
        val deserialized = json.decodeFromString(MapGetResponse.serializer(), serialized)

        assertTrue(serialized.contains("schemaVersion"))
        assertTrue(serialized.contains("stateVersion"))
        assertTrue(serialized.contains("definition"))
        assertTrue(serialized.contains("territoryStates"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            MapGetErrorResponse(
                code = MapGetErrorCode.GAME_NOT_FOUND,
                reason = "Lobby 'ZZ99' wurde nicht gefunden.",
            )

        val serialized = json.encodeToString(MapGetErrorResponse.serializer(), response)
        val deserialized = json.decodeFromString(MapGetErrorResponse.serializer(), serialized)

        assertEquals(
            """{"code":"GAME_NOT_FOUND","reason":"Lobby 'ZZ99' wurde nicht gefunden."}""",
            serialized,
        )
        assertEquals(response, deserialized)
    }

    private fun sampleResponse(): MapGetResponse =
        MapGetResponse(
            lobbyCode = LobbyCode("AB12"),
            schemaVersion = 1,
            mapHash = "abc123",
            stateVersion = 7,
            definition =
                MapDefinitionSnapshot(
                    territories =
                        listOf(
                            MapTerritoryDefinitionSnapshot(
                                territoryId = TerritoryId("alpha"),
                                edges =
                                    listOf(
                                        MapTerritoryEdgeSnapshot(targetId = TerritoryId("beta")),
                                    ),
                            ),
                            MapTerritoryDefinitionSnapshot(
                                territoryId = TerritoryId("beta"),
                                edges =
                                    listOf(
                                        MapTerritoryEdgeSnapshot(targetId = TerritoryId("alpha")),
                                    ),
                            ),
                        ),
                    continents =
                        listOf(
                            MapContinentDefinitionSnapshot(
                                continentId = ContinentId("north"),
                                territoryIds = listOf(TerritoryId("alpha"), TerritoryId("beta")),
                                bonusValue = 3,
                            ),
                        ),
                ),
            territoryStates =
                listOf(
                    MapTerritoryStateSnapshot(
                        territoryId = TerritoryId("alpha"),
                        ownerId = PlayerId(1),
                        troopCount = 5,
                    ),
                    MapTerritoryStateSnapshot(
                        territoryId = TerritoryId("beta"),
                        ownerId = null,
                        troopCount = 2,
                    ),
                ),
        )
}
