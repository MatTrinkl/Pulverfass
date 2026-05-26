package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapCommandTest {
    private val lobbyCode = LobbyCode("AB12")
    private val playerId = PlayerId(1)
    private val alpha = TerritoryId("alpha")
    private val beta = TerritoryId("beta")

    @Test
    fun `place troops command requires positive troop count`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                PlaceTroopsCommand(lobbyCode, playerId, alpha, troopCount = 0)
            }

        assertEquals(
            "PlaceTroopsCommand.troopCount muss positiv sein, war aber 0.",
            exception.message,
        )
    }

    @Test
    fun `move troops command validates troop count and distinct territories`() {
        assertThrows(IllegalArgumentException::class.java) {
            MoveTroopsCommand(lobbyCode, playerId, alpha, beta, troopCount = 0)
        }

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                MoveTroopsCommand(lobbyCode, playerId, alpha, alpha, troopCount = 1)
            }

        assertTrue(exception.message.orEmpty().contains("unterschiedliche Territorien"))
    }

    @Test
    fun `attack command validates requested dice occupation and distinct territories`() {
        assertThrows(IllegalArgumentException::class.java) {
            AttackCommand(lobbyCode, playerId, alpha, alpha)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AttackCommand(lobbyCode, playerId, alpha, beta, requestedAttackDice = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AttackCommand(lobbyCode, playerId, alpha, beta, requestedAttackDice = 4)
        }

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                AttackCommand(
                    lobbyCode = lobbyCode,
                    playerId = playerId,
                    fromTerritoryId = alpha,
                    toTerritoryId = beta,
                    occupyingTroopCount = 0,
                )
            }

        assertEquals(
            "AttackCommand.occupyingTroopCount muss positiv sein, war aber 0.",
            exception.message,
        )
    }
}
