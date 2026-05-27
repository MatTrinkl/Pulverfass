package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.request.ConfirmReinforcementsDoneRequest
import at.aau.pulverfass.shared.message.lobby.response.ConfirmReinforcementsDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfirmReinforcementsDoneMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request =
            ConfirmReinforcementsDoneRequest(
                lobbyCode = LobbyCode("AB12"),
                playerId = PlayerId(7),
            )

        val serialized =
            json.encodeToString(ConfirmReinforcementsDoneRequest.serializer(), request)
        val deserialized =
            json.decodeFromString(ConfirmReinforcementsDoneRequest.serializer(), serialized)

        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response = ConfirmReinforcementsDoneResponse(lobbyCode = LobbyCode("CD34"))

        val serialized =
            json.encodeToString(ConfirmReinforcementsDoneResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(ConfirmReinforcementsDoneResponse.serializer(), serialized)

        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            ConfirmReinforcementsDoneErrorResponse(
                code = ConfirmReinforcementsDoneErrorCode.PENDING_REINFORCEMENTS_REMAINING,
                reason = "Noch offene Verstärkungen vorhanden.",
            )

        val serialized =
            json.encodeToString(ConfirmReinforcementsDoneErrorResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(
                ConfirmReinforcementsDoneErrorResponse.serializer(),
                serialized,
            )

        assertTrue(serialized.contains("PENDING_REINFORCEMENTS_REMAINING"))
        assertEquals(response, deserialized)
    }
}
