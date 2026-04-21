package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameStatePrivateGetCodecTest {
    @Test
    fun `should encode and decode private get request payload directly`() {
        val payload = GameStatePrivateGetRequest(LobbyCode("PG12"), PlayerId(5))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode private get response payload directly`() {
        val payload =
            GameStatePrivateGetResponse(
                lobbyCode = LobbyCode("PG34"),
                recipientPlayerId = PlayerId(5),
                stateVersion = 12,
                handCards = listOf("infantry", "artillery"),
                secretObjectives = listOf("hold_asia"),
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode private get error payload directly`() {
        val payload =
            GameStatePrivateGetErrorResponse(
                code = GameStatePrivateGetErrorCode.REQUESTER_MISMATCH,
                reason = "Requester '2' passt nicht zur aktuellen Connection '1'.",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }
}
