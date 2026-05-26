package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameStateDeltaMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip game state delta with deterministic event order`() {
        val delta =
            GameStateDeltaEvent(
                lobbyCode = LobbyCode("AB12"),
                fromVersion = 4,
                toVersion = 4,
                events =
                    listOf(
                        AttackResolvedEvent(
                            lobbyCode = LobbyCode("AB12"),
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
                            stateVersion = 4,
                        ),
                        TerritoryOwnerChangedEvent(
                            lobbyCode = LobbyCode("AB12"),
                            territoryId = TerritoryId("alpha"),
                            ownerId = PlayerId(7),
                            stateVersion = 4,
                        ),
                        TurnStateUpdatedEvent(
                            lobbyCode = LobbyCode("AB12"),
                            activePlayerId = PlayerId(8),
                            turnPhase = TurnPhase.FORTIFY,
                            turnCount = 2,
                            startPlayerId = PlayerId(7),
                        ),
                    ),
            )

        val serialized = json.encodeToString(GameStateDeltaEvent.serializer(), delta)
        val deserialized = json.decodeFromString(GameStateDeltaEvent.serializer(), serialized)

        assertTrue(serialized.contains("fromVersion"))
        assertTrue(serialized.contains("toVersion"))
        assertTrue(serialized.contains("messageType"))
        assertEquals(delta, deserialized)
    }
}
