package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StartPlayerSetMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request =
            StartPlayerSetRequest(
                lobbyCode = LobbyCode("AB12"),
                startPlayerId = PlayerId(2),
                requesterPlayerId = PlayerId(1),
            )

        val serialized = json.encodeToString(StartPlayerSetRequest.serializer(), request)
        val deserialized = json.decodeFromString(StartPlayerSetRequest.serializer(), serialized)

        assertTrue(serialized.contains("startPlayerId"))
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response = StartPlayerSetResponse(LobbyCode("CD34"), PlayerId(7))

        val serialized = json.encodeToString(StartPlayerSetResponse.serializer(), response)
        val deserialized = json.decodeFromString(StartPlayerSetResponse.serializer(), serialized)

        assertEquals("""{"lobbyCode":"CD34","startPlayerId":7}""", serialized)
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            StartPlayerSetErrorResponse(
                code = StartPlayerSetErrorCode.NOT_HOST,
                reason = "Nur der Lobby Owner darf den Startspieler setzen.",
            )

        val serialized = json.encodeToString(StartPlayerSetErrorResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(
                StartPlayerSetErrorResponse.serializer(),
                serialized,
            )

        assertTrue(serialized.contains("NOT_HOST"))
        assertEquals(response, deserialized)
    }
}
