package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
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
