package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.request.LobbyPlayerCountRequest
import at.aau.pulverfass.shared.message.lobby.response.LobbyPlayerCountResponse
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LobbyPlayerCountMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request = LobbyPlayerCountRequest(lobbyCode = LobbyCode("AB12"))

        val serialized = json.encodeToString(LobbyPlayerCountRequest.serializer(), request)
        val deserialized =
            json.decodeFromString(LobbyPlayerCountRequest.serializer(), serialized)

        assertEquals("""{"lobbyCode":"AB12"}""", serialized)
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response =
            LobbyPlayerCountResponse(
                lobbyCode = LobbyCode("CD34"),
                playerCount = 4,
            )

        val serialized = json.encodeToString(LobbyPlayerCountResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(LobbyPlayerCountResponse.serializer(), serialized)

        assertEquals("""{"lobbyCode":"CD34","playerCount":4}""", serialized)
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            LobbyPlayerCountErrorResponse(
                lobbyCode = LobbyCode("ZZ99"),
                code = LobbyPlayerCountErrorCode.LOBBY_NOT_FOUND,
                reason = "Lobby 'ZZ99' wurde nicht gefunden.",
            )

        val serialized =
            json.encodeToString(LobbyPlayerCountErrorResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(LobbyPlayerCountErrorResponse.serializer(), serialized)

        assertEquals(
            """
            {"lobbyCode":"ZZ99","code":"LOBBY_NOT_FOUND","reason":"Lobby 'ZZ99' wurde nicht gefunden."}
            """.trimIndent(),
            serialized,
        )
        assertEquals(response, deserialized)
    }
}
