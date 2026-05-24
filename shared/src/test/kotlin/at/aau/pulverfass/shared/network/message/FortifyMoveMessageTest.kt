package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.lobby.request.FortifyMoveRequest
import at.aau.pulverfass.shared.message.lobby.response.FortifyMoveResponse
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FortifyMoveMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip fortify move request`() {
        val request =
            FortifyMoveRequest(
                lobbyCode = LobbyCode("FM12"),
                playerId = PlayerId(7),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("beta"),
                troopCount = 3,
            )

        val serialized = json.encodeToString(FortifyMoveRequest.serializer(), request)
        val deserialized = json.decodeFromString(FortifyMoveRequest.serializer(), serialized)

        assertTrue(serialized.contains("fromTerritoryId"))
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip fortify move response`() {
        val response = FortifyMoveResponse(lobbyCode = LobbyCode("FM34"))

        val serialized = json.encodeToString(FortifyMoveResponse.serializer(), response)
        val deserialized = json.decodeFromString(FortifyMoveResponse.serializer(), serialized)

        assertEquals("""{"lobbyCode":"FM34"}""", serialized)
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip fortify move error response`() {
        val response =
            FortifyMoveErrorResponse(
                code = FortifyMoveErrorCode.NO_PATH,
                reason = "Fortify benötigt einen zusammenhängenden Pfad über eigene Gebiete.",
            )

        val serialized = json.encodeToString(FortifyMoveErrorResponse.serializer(), response)
        val deserialized = json.decodeFromString(FortifyMoveErrorResponse.serializer(), serialized)

        assertTrue(serialized.contains("NO_PATH"))
        assertEquals(response, deserialized)
    }
}
