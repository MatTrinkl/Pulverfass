package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequest
import at.aau.pulverfass.shared.message.lobby.request.TerritoryPlacement
import at.aau.pulverfass.shared.message.lobby.response.PlaceReinforcementsResponse
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaceReinforcementsMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request =
            PlaceReinforcementsRequest(
                lobbyCode = LobbyCode("1003"),
                playerId = PlayerId(7),
                placements =
                    listOf(
                        TerritoryPlacement(TerritoryId("alpha"), 2),
                        TerritoryPlacement(TerritoryId("beta"), 3),
                    ),
            )

        val serialized = json.encodeToString(PlaceReinforcementsRequest.serializer(), request)
        val deserialized =
            json.decodeFromString(PlaceReinforcementsRequest.serializer(), serialized)

        assertTrue(serialized.contains("placements"))
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response = PlaceReinforcementsResponse(lobbyCode = LobbyCode("1071"))

        val serialized = json.encodeToString(PlaceReinforcementsResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(PlaceReinforcementsResponse.serializer(), serialized)

        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            PlaceReinforcementsErrorResponse(
                code = PlaceReinforcementsErrorCode.OVERSPEND,
                reason = "Zu viele Truppen angefordert.",
            )

        val serialized =
            json.encodeToString(PlaceReinforcementsErrorResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(PlaceReinforcementsErrorResponse.serializer(), serialized)

        assertTrue(serialized.contains("OVERSPEND"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip pending reinforcements changed event`() {
        val event =
            PendingReinforcementsChangedEvent(
                lobbyCode = LobbyCode("1132"),
                playerId = PlayerId(4),
                delta = -3,
            )

        val serialized =
            json.encodeToString(PendingReinforcementsChangedEvent.serializer(), event)
        val deserialized =
            json.decodeFromString(PendingReinforcementsChangedEvent.serializer(), serialized)

        assertTrue(serialized.contains("delta"))
        assertEquals(event, deserialized)
    }
}
