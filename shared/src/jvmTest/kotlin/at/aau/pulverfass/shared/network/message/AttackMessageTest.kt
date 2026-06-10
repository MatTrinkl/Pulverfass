package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.request.AttackRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmAttackDoneRequest
import at.aau.pulverfass.shared.message.lobby.response.AttackResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmAttackDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `attack request rejects less than minimum attack troops`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                AttackRequest(
                    lobbyCode = LobbyCode("AT12"),
                    playerId = PlayerId(7),
                    fromTerritoryId = TerritoryId("alpha"),
                    toTerritoryId = TerritoryId("beta"),
                    attackTroops = 1,
                    moveAfterCapture = 1,
                    requestId = "req-invalid",
                )
            }

        assertEquals(
            "AttackRequest.attackTroops muss mindestens 2 sein, war aber 1.",
            exception.message,
        )
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
    fun `serializer roundtrip confirm attack done request`() {
        val request =
            ConfirmAttackDoneRequest(
                lobbyCode = LobbyCode("AT90"),
                playerId = PlayerId(7),
            )

        val serialized = json.encodeToString(ConfirmAttackDoneRequest.serializer(), request)
        val deserialized =
            json.decodeFromString(ConfirmAttackDoneRequest.serializer(), serialized)

        assertTrue(serialized.contains("lobbyCode"))
        assertTrue(serialized.contains("playerId"))
        assertEquals(request, deserialized)
    }

    @Test
    fun `confirm attack done request rejects missing required field`() {
        assertThrows(kotlinx.serialization.MissingFieldException::class.java) {
            json.decodeFromString(
                ConfirmAttackDoneRequest.serializer(),
                """{"lobbyCode":"AT90"}""",
            )
        }
    }

    @Test
    fun `serializer roundtrip confirm attack done response`() {
        val response = ConfirmAttackDoneResponse(lobbyCode = LobbyCode("AT91"))

        val serialized = json.encodeToString(ConfirmAttackDoneResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(ConfirmAttackDoneResponse.serializer(), serialized)

        assertTrue(serialized.contains("lobbyCode"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `confirm attack done response rejects missing required field`() {
        assertThrows(kotlinx.serialization.MissingFieldException::class.java) {
            json.decodeFromString(
                ConfirmAttackDoneResponse.serializer(),
                """{}""",
            )
        }
    }

    @Test
    fun `serializer roundtrip confirm attack done error response`() {
        val response =
            ConfirmAttackDoneErrorResponse(
                code = ConfirmAttackDoneErrorCode.PHASE_MISMATCH,
                reason = "Attack phase is already closed.",
            )

        val serialized =
            json.encodeToString(ConfirmAttackDoneErrorResponse.serializer(), response)
        val deserialized =
            json.decodeFromString(ConfirmAttackDoneErrorResponse.serializer(), serialized)

        assertTrue(serialized.contains("PHASE_MISMATCH"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `confirm attack done error response rejects missing required field`() {
        assertThrows(kotlinx.serialization.MissingFieldException::class.java) {
            json.decodeFromString(
                ConfirmAttackDoneErrorResponse.serializer(),
                """{"code":"NOT_ACTIVE_PLAYER"}""",
            )
        }
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
    fun `attack resolved broadcast strips rng data and marks capture`() {
        val internalEvent =
            AttackResolvedEvent(
                lobbyCode = LobbyCode("AT58"),
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
            )

        val broadcast = AttackResolvedBroadcastEvent.from(internalEvent, stateVersion = 19L)
        val serialized =
            json.encodeToString(AttackResolvedBroadcastEvent.serializer(), broadcast)

        assertTrue(broadcast.capture)
        assertTrue(serialized.contains("attackerRolls"))
        assertFalse(serialized.contains("rngTrace"))
        assertFalse(serialized.contains("rngStateBefore"))
        assertFalse(serialized.contains("rngStateAfter"))
        assertTrue(serialized.contains("\"stateVersion\":19"))
    }

    @Test
    fun `attack resolved event enforces capture occupation invariants`() {
        assertThrows(IllegalArgumentException::class.java) {
            AttackResolvedEvent(
                lobbyCode = LobbyCode("AT59"),
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
                occupyingTroopCount = null,
                minOccupyingTroops = 3,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            AttackResolvedEvent(
                lobbyCode = LobbyCode("AT60"),
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
                attackerLosses = 1,
                defenderLosses = 1,
                attackerRemaining = 4,
                defenderRemaining = 1,
                occupyingTroopCount = 2,
                minOccupyingTroops = null,
            )
        }
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
