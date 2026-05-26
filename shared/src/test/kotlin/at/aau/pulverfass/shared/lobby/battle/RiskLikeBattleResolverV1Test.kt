package at.aau.pulverfass.shared.lobby.battle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class RiskLikeBattleResolverV1Test {
    private val resolver = RiskLikeBattleResolverV1()

    private fun battleRng(
        seed: Long,
        stateVersion: Long,
    ) = BattleRngFactory.fromState(seed xor stateVersion)

    @Test
    fun `same seed and input produce same outcome`() {
        val first =
            resolver.resolve(
                attackTroops = 5,
                defendTroops = 2,
                rng = battleRng(1L, 17L),
            )
        val second =
            resolver.resolve(
                attackTroops = 5,
                defendTroops = 2,
                rng = battleRng(1L, 17L),
            )

        assertEquals(first, second)
    }

    @Test
    fun `different seed produces plausibly different outcome`() {
        val first =
            resolver.resolve(
                attackTroops = 5,
                defendTroops = 2,
                rng = battleRng(1L, 17L),
            )
        val second =
            resolver.resolve(
                attackTroops = 5,
                defendTroops = 2,
                rng = battleRng(2L, 17L),
            )

        assertNotEquals(first.rngTrace, second.rngTrace)
    }

    @Test
    fun `golden vector capture with three attack dice versus two defense dice`() {
        val outcome =
            resolver.resolve(
                attackTroops = 5,
                defendTroops = 2,
                rng = battleRng(0x1L, 3L),
            )

        assertEquals(
            BattleOutcome(
                attackerRemaining = 5,
                defenderRemaining = 0,
                rngTrace = listOf(5, 3, 4, 1, 2),
                attackDice = 3,
                defendDice = 2,
                attackerRolls = listOf(5, 4, 3),
                defenderRolls = listOf(2, 1),
                attackerLosses = 0,
                defenderLosses = 2,
                minOccupyingTroops = 3,
            ),
            outcome,
        )
    }

    @Test
    fun `golden vector defender wins ties`() {
        val outcome =
            resolver.resolve(
                attackTroops = 5,
                defendTroops = 2,
                rng = battleRng(0x1L, 17L),
            )

        assertEquals(
            BattleOutcome(
                attackerRemaining = 4,
                defenderRemaining = 1,
                rngTrace = listOf(6, 2, 5, 5, 5),
                attackDice = 3,
                defendDice = 2,
                attackerRolls = listOf(6, 5, 2),
                defenderRolls = listOf(5, 5),
                attackerLosses = 1,
                defenderLosses = 1,
                minOccupyingTroops = null,
            ),
            outcome,
        )
    }

    @Test
    fun `golden vector defender loses two troops without capture`() {
        val outcome =
            resolver.resolve(
                attackTroops = 5,
                defendTroops = 4,
                rng = battleRng(0x1L, 18L),
            )

        assertEquals(
            BattleOutcome(
                attackerRemaining = 5,
                defenderRemaining = 2,
                rngTrace = listOf(1, 3, 6, 2, 1),
                attackDice = 3,
                defendDice = 2,
                attackerRolls = listOf(6, 3, 1),
                defenderRolls = listOf(2, 1),
                attackerLosses = 0,
                defenderLosses = 2,
                minOccupyingTroops = null,
            ),
            outcome,
        )
    }

    @Test
    fun `golden vector single defender troop uses one defense die`() {
        val outcome =
            resolver.resolve(
                attackTroops = 4,
                defendTroops = 1,
                rng = battleRng(0x2AL, 41L),
            )

        assertEquals(
            BattleOutcome(
                attackerRemaining = 3,
                defenderRemaining = 1,
                rngTrace = listOf(4, 4, 4, 6),
                attackDice = 3,
                defendDice = 1,
                attackerRolls = listOf(4, 4, 4),
                defenderRolls = listOf(6),
                attackerLosses = 1,
                defenderLosses = 0,
                minOccupyingTroops = null,
            ),
            outcome,
        )
    }

    @Test
    fun `golden vector attack dice are capped by source troops minus one`() {
        val outcome =
            resolver.resolve(
                attackTroops = 3,
                defendTroops = 2,
                rng = battleRng(0x123456789ABCDEF0, 7L),
            )

        assertEquals(
            BattleOutcome(
                attackerRemaining = 2,
                defenderRemaining = 1,
                rngTrace = listOf(1, 6, 5, 5),
                attackDice = 2,
                defendDice = 2,
                attackerRolls = listOf(6, 1),
                defenderRolls = listOf(5, 5),
                attackerLosses = 1,
                defenderLosses = 1,
                minOccupyingTroops = null,
            ),
            outcome,
        )
    }

    @Test
    fun `golden vector large seed remains replayable`() {
        val outcome =
            resolver.resolve(
                attackTroops = 8,
                defendTroops = 6,
                rng = battleRng(0xCAFEBABEDEADBEEFuL.toLong(), 1234L),
            )

        assertEquals(
            BattleOutcome(
                attackerRemaining = 8,
                defenderRemaining = 4,
                rngTrace = listOf(5, 2, 4, 4, 1),
                attackDice = 3,
                defendDice = 2,
                attackerRolls = listOf(5, 4, 2),
                defenderRolls = listOf(4, 1),
                attackerLosses = 0,
                defenderLosses = 2,
                minOccupyingTroops = null,
            ),
            outcome,
        )
    }

    @Test
    fun `v1 config remains intentionally swappable`() {
        val outcome =
            RiskLikeBattleResolverV1(
                config =
                    RiskLikeBattleResolverV1Config(
                        maxAttackDice = 2,
                        maxDefendDice = 1,
                    ),
            ).resolve(
                attackTroops = 5,
                defendTroops = 4,
                rng = battleRng(0x1L, 18L),
            )

        assertEquals(2, outcome.attackDice)
        assertEquals(1, outcome.defendDice)
        assertEquals(listOf(1, 3, 6), outcome.rngTrace)
    }
}
