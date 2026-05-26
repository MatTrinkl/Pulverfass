package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.message.lobby.request.AttackRequest
import at.aau.pulverfass.shared.message.lobby.response.AttackResponse
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttackMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip attack request`() {
        val request =
            AttackRequest(
                lobbyCode = LobbyCode("AT12"),
                playerId = PlayerId(7),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("beta"),
                attackTroops = 3,
                moveAfterCapture = 3,
                requestId = "req-1",
            )

        val serialized = json.encodeToString(AttackRequest.serializer(), request)
        val deserialized = json.decodeFromString(AttackRequest.serializer(), serialized)

        assertTrue(serialized.contains("attackTroops"))
        assertTrue(serialized.contains("moveAfterCapture"))
        assertTrue(serialized.contains("requestId"))
        assertEquals(request, deserialized)
    }

    @Test
    fun `serializer roundtrip attack response`() {
        val response = AttackResponse(lobbyCode = LobbyCode("AT34"), requestId = "req-2")

        val serialized = json.encodeToString(AttackResponse.serializer(), response)
        val deserialized = json.decodeFromString(AttackResponse.serializer(), serialized)

        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip attack error response`() {
        val response =
            AttackErrorResponse(
                code = AttackErrorCode.NOT_ADJACENT,
                reason = "Ein Angriff ist nur zwischen direkt benachbarten Territorien erlaubt.",
                requestId = "req-3",
            )

        val serialized = json.encodeToString(AttackErrorResponse.serializer(), response)
        val deserialized = json.decodeFromString(AttackErrorResponse.serializer(), serialized)

        assertTrue(serialized.contains("NOT_ADJACENT"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `serializer roundtrip attack resolved event`() {
        val event =
            AttackResolvedEvent(
                lobbyCode = LobbyCode("AT56"),
                attackerPlayerId = PlayerId(7),
                defenderPlayerId = PlayerId(8),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("beta"),
                attackTroops = 3,
                sourceTroopsBefore = 5,
                targetTroopsBefore = 2,
                requestedAttackDice = 3,
                attackDice = 3,
                defendDice = 2,
                attackerRolls = listOf(5, 4, 3),
                defenderRolls = listOf(2, 1),
                rngTrace = listOf(5, 3, 4, 1, 2),
                rngStateBefore = 2L,
                rngStateAfter = 3L,
                attackerLosses = 0,
                defenderLosses = 2,
                attackerRemaining = 5,
                defenderRemaining = 0,
                occupyingTroopCount = 3,
                minOccupyingTroops = 3,
                stateVersion = 11L,
            )

        val serialized = json.encodeToString(AttackResolvedEvent.serializer(), event)
        val deserialized = json.decodeFromString(AttackResolvedEvent.serializer(), serialized)

        assertTrue(serialized.contains("attackTroops"))
        assertTrue(serialized.contains("rngTrace"))
        assertTrue(serialized.contains("stateVersion"))
        assertEquals(event, deserialized)
    }

    @Test
    fun `serializer roundtrip player eliminated event`() {
        val event =
            PlayerEliminatedEvent(
                lobbyCode = LobbyCode("AT57"),
                playerId = PlayerId(8),
                eliminatedByPlayerId = PlayerId(7),
                stateVersion = 12L,
            )

        val serialized = json.encodeToString(PlayerEliminatedEvent.serializer(), event)
        val deserialized = json.decodeFromString(PlayerEliminatedEvent.serializer(), serialized)

        assertTrue(serialized.contains("eliminatedByPlayerId"))
        assertEquals(event, deserialized)
    }
}
