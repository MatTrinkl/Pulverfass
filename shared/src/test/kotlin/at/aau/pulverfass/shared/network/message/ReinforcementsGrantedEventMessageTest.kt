package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReinforcementsGrantedEventMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip reinforcements granted event`() {
        val event =
            ReinforcementsGrantedEvent(
                lobbyCode = LobbyCode("RG12"),
                playerId = PlayerId(7),
                amount = 5,
                territoryBonus = 3,
                continentBonus = 2,
                cardBonus = 0,
            )

        val serialized = json.encodeToString(ReinforcementsGrantedEvent.serializer(), event)
        val deserialized =
            json.decodeFromString(ReinforcementsGrantedEvent.serializer(), serialized)

        assertEquals(event, deserialized)
    }
}
