package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TerritoryStateTest {
    @Test
    fun `should expose default territory state`() {
        val territoryState = TerritoryState(TerritoryId("alpha"))

        // Prüft den Standardzustand eines Territoriums ohne Besitzer und ohne Truppen.
        assertEquals(TerritoryId("alpha"), territoryState.territoryId)
        assertNull(territoryState.ownerId)
        assertEquals(0, territoryState.troopCount)
    }

    @Test
    fun `should store owner and troop count`() {
        val playerOne = PlayerId(1)
        val territoryState =
            TerritoryState(
                territoryId = TerritoryId("beta"),
                ownerId = playerOne,
                troopCount = 5,
            )

        // Prüft, dass Besitzer und Truppenanzahl im TerritoryState gespeichert werden.
        assertEquals(TerritoryId("beta"), territoryState.territoryId)
        assertEquals(playerOne, requireNotNull(territoryState.ownerId))
        assertEquals(5, territoryState.troopCount)
    }

    @Test
    fun `should allow zero troop count with owner`() {
        val playerOne = PlayerId(1)
        val territoryState =
            TerritoryState(
                territoryId = TerritoryId("delta"),
                ownerId = playerOne,
                troopCount = 0,
            )

        // Prüft, dass ein Besitzer auch mit 0 Truppen gespeichert werden kann.
        assertEquals(playerOne, requireNotNull(territoryState.ownerId))
        assertEquals(0, territoryState.troopCount)
    }

    @Test
    fun `should throw when troop count is negative`() {
        // Prüft, dass ein Territorium keine negative Truppenanzahl haben darf.
        assertThrows(IllegalArgumentException::class.java) {
            TerritoryState(
                territoryId = TerritoryId("gamma"),
                troopCount = -1,
            )
        }
    }
}
