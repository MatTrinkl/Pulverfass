package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.request.ClaimCheatReinforcementBonusRequest
import at.aau.pulverfass.shared.message.lobby.response.ClaimCheatReinforcementBonusResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ClaimCheatReinforcementBonusErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ClaimCheatReinforcementBonusErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClaimCheatReinforcementBonusMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip request`() {
        val request =
            ClaimCheatReinforcementBonusRequest(
                lobbyCode = LobbyCode("AB12"),
                playerId = PlayerId(7),
            )

        val serialized =
            json.encodeToString(ClaimCheatReinforcementBonusRequest.serializer(), request)
        val deserialized =
            json.decodeFromString(ClaimCheatReinforcementBonusRequest.serializer(), serialized)

        assertTrue(serialized.contains("playerId"))
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip response`() {
        val response = ClaimCheatReinforcementBonusResponse(lobbyCode = LobbyCode("CD34"))

        val serialized =
            json.encodeToString(ClaimCheatReinforcementBonusResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(ClaimCheatReinforcementBonusResponse.serializer(), serialized)

        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip error response`() {
        val response =
            ClaimCheatReinforcementBonusErrorResponse(
                code = ClaimCheatReinforcementBonusErrorCode.ALREADY_USED,
                reason = "Der Schummel-Verstärkungsbonus wurde bereits verwendet.",
            )

        val serialized =
            json.encodeToString(ClaimCheatReinforcementBonusErrorResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(
                ClaimCheatReinforcementBonusErrorResponse.serializer(),
                serialized,
            )

        assertTrue(serialized.contains("ALREADY_USED"))
        assertEquals(response, deserialized)
    }
}
