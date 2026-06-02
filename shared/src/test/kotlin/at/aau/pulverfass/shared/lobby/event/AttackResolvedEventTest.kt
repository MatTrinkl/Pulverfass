package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttackResolvedEventTest {
    @Test
    fun `valid non capture attack event is accepted`() {
        val event = validEvent()

        assertFalse(event.capture)
        assertEquals(null, event.occupyingTroopCount)
        assertEquals(null, event.minOccupyingTroops)
    }

    @Test
    fun `valid capture attack event is accepted`() {
        val event =
            validEvent(
                attackerLosses = 0,
                defenderLosses = 2,
                attackerRemaining = 5,
                defenderRemaining = 0,
                occupyingTroopCount = 3,
                minOccupyingTroops = 3,
            )

        assertTrue(event.capture)
        assertEquals(3, event.occupyingTroopCount)
        assertEquals(3, event.minOccupyingTroops)
    }

    @Test
    fun `attack resolved event rejects invalid invariants`() {
        val invalidCases =
            listOf(
                { validEvent(attackTroops = 1) },
                {
                    validEvent(
                        sourceTroopsBefore = 2,
                        attackerRemaining = 2,
                        attackTroops = 2,
                    )
                },
                {
                    validEvent(
                        targetTroopsBefore = 0,
                        defenderRemaining = 0,
                        defenderLosses = 0,
                    )
                },
                { validEvent(attackTroops = 5) },
                { validEvent(requestedAttackDice = 0) },
                { validEvent(requestedAttackDice = 4) },
                { validEvent(requestedAttackDice = 3, attackTroops = 2, attackDice = 3) },
                {
                    validEvent(
                        attackDice = 0,
                        attackerRolls = emptyList(),
                        rngTrace = listOf(2, 1),
                    )
                },
                {
                    validEvent(
                        defendDice = 0,
                        defenderRolls = emptyList(),
                        rngTrace = listOf(6, 5, 4),
                    )
                },
                { validEvent(requestedAttackDice = 2, attackDice = 3) },
                { validEvent(attackerLosses = -1, attackerRemaining = 6) },
                { validEvent(defenderLosses = -1, defenderRemaining = 3) },
                { validEvent(attackerRemaining = 0, attackerLosses = 5) },
                { validEvent(defenderRemaining = -1, defenderLosses = 3) },
                { validEvent(attackerRolls = listOf(6, 5)) },
                { validEvent(defenderRolls = listOf(2)) },
                { validEvent(rngTrace = listOf(6, 5, 4, 2)) },
                { validEvent(attackerRemaining = 3) },
                { validEvent(defenderRemaining = 3) },
                { validEvent(attackerLosses = 0, defenderLosses = 0, defenderRemaining = 2) },
                {
                    validEvent(
                        occupyingTroopCount = 0,
                        defenderRemaining = 0,
                        defenderLosses = 2,
                        minOccupyingTroops = 3,
                    )
                },
                {
                    validEvent(
                        occupyingTroopCount = 4,
                        defenderRemaining = 0,
                        defenderLosses = 2,
                        minOccupyingTroops = 3,
                    )
                },
                {
                    validEvent(
                        defenderRemaining = 0,
                        defenderLosses = 2,
                        occupyingTroopCount = null,
                        minOccupyingTroops = 3,
                    )
                },
                {
                    validEvent(
                        defenderRemaining = 1,
                        defenderLosses = 1,
                        occupyingTroopCount = 2,
                    )
                },
                {
                    validEvent(
                        defenderRemaining = 0,
                        defenderLosses = 2,
                        occupyingTroopCount = 3,
                        minOccupyingTroops = null,
                    )
                },
                { validEvent(defenderRemaining = 1, defenderLosses = 1, minOccupyingTroops = 1) },
                { validEvent(stateVersion = -1) },
            )

        invalidCases.forEach { builder ->
            assertThrows(IllegalArgumentException::class.java) {
                builder()
            }
        }
    }

    private fun validEvent(
        attackTroops: Int = 3,
        sourceTroopsBefore: Int = 5,
        targetTroopsBefore: Int = 2,
        requestedAttackDice: Int = 3,
        attackDice: Int = 3,
        defendDice: Int = 2,
        attackerRolls: List<Int> = listOf(6, 5, 4),
        defenderRolls: List<Int> = listOf(2, 1),
        rngTrace: List<Int> = listOf(6, 5, 4, 2, 1),
        attackerLosses: Int = 1,
        defenderLosses: Int = 1,
        attackerRemaining: Int = 4,
        defenderRemaining: Int = 1,
        occupyingTroopCount: Int? = null,
        minOccupyingTroops: Int? = null,
        stateVersion: Long? = 7L,
    ) = AttackResolvedEvent(
        lobbyCode = LobbyCode("AR11"),
        attackerPlayerId = PlayerId(1),
        defenderPlayerId = PlayerId(2),
        fromTerritoryId = TerritoryId("alpha"),
        toTerritoryId = TerritoryId("beta"),
        attackTroops = attackTroops,
        sourceTroopsBefore = sourceTroopsBefore,
        targetTroopsBefore = targetTroopsBefore,
        requestedAttackDice = requestedAttackDice,
        attackDice = attackDice,
        defendDice = defendDice,
        attackerRolls = attackerRolls,
        defenderRolls = defenderRolls,
        rngTrace = rngTrace,
        rngStateBefore = 11L,
        rngStateAfter = 12L,
        attackerLosses = attackerLosses,
        defenderLosses = defenderLosses,
        attackerRemaining = attackerRemaining,
        defenderRemaining = defenderRemaining,
        occupyingTroopCount = occupyingTroopCount,
        minOccupyingTroops = minOccupyingTroops,
        stateVersion = stateVersion,
    )
}
