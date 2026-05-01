package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapDeltaEventMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip territory owner changed event`() {
        val event =
            TerritoryOwnerChangedEvent(
                lobbyCode = LobbyCode("AB12"),
                territoryId = TerritoryId("alpha"),
                ownerId = PlayerId(7),
                stateVersion = 41,
            )

        val serialized = json.encodeToString(TerritoryOwnerChangedEvent.serializer(), event)
        val deserialized =
            json.decodeFromString(
                TerritoryOwnerChangedEvent.serializer(),
                serialized,
            )

        assertTrue(serialized.contains("stateVersion"))
        assertEquals(event, deserialized)
    }

    @Test
    fun `serializer roundtrip territory troops changed event`() {
        val event =
            TerritoryTroopsChangedEvent(
                lobbyCode = LobbyCode("CD34"),
                territoryId = TerritoryId("beta"),
                troopCount = 5,
                stateVersion = 42,
            )

        val serialized = json.encodeToString(TerritoryTroopsChangedEvent.serializer(), event)
        val deserialized =
            json.decodeFromString(
                TerritoryTroopsChangedEvent.serializer(),
                serialized,
            )

        assertTrue(serialized.contains("stateVersion"))
        assertEquals(event, deserialized)
    }
}
