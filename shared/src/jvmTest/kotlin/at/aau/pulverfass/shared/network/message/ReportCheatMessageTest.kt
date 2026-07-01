package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.request.ReportCheatRequest
import at.aau.pulverfass.shared.message.lobby.response.ReportCheatResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ReportCheatErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ReportCheatErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Prüft den Netzwerkvertrag für Cheat-Meldungen.
 *
 * Besonders wichtig sind hier reporterPlayerId und accusedPlayerId: Beide IDs
 * müssen über das Netzwerk erhalten bleiben, weil der Server daraus ableitet,
 * wer meldet und wer beschuldigt wird.
 */
class ReportCheatMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request =
            ReportCheatRequest(
                lobbyCode = LobbyCode("1321"),
                reporterPlayerId = PlayerId(2),
                accusedPlayerId = PlayerId(1),
            )

        val serialized = json.encodeToString(ReportCheatRequest.serializer(), request)
        val deserialized = json.decodeFromString(ReportCheatRequest.serializer(), serialized)

        assertTrue(serialized.contains("reporterPlayerId"))
        assertTrue(serialized.contains("accusedPlayerId"))
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response =
            ReportCheatResponse(
                lobbyCode = LobbyCode("1321"),
                accusedPlayerId = PlayerId(1),
                correct = true,
                modifierDelta = 3,
            )

        val serialized = json.encodeToString(ReportCheatResponse.serializer(), response)
        val deserialized = json.decodeFromString(ReportCheatResponse.serializer(), serialized)

        assertTrue(serialized.contains("modifierDelta"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            ReportCheatErrorResponse(
                code = ReportCheatErrorCode.ALREADY_REPORTED,
                reason = "Dieser Cheat wurde bereits gemeldet.",
            )

        val serialized = json.encodeToString(ReportCheatErrorResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(ReportCheatErrorResponse.serializer(), serialized)

        assertTrue(serialized.contains("ALREADY_REPORTED"))
        assertEquals(response, deserialized)
    }
}
