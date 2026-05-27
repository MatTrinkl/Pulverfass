package at.aau.pulverfass.shared.lobby.battle

/**
 * Aktuelle, klar markierte v1-Implementierung der Battle-Strategie.
 *
 * Die Default-Konfiguration entspricht der dokumentierten Kampf-Formel aus
 * `docs/architecture/battle-resolution.md`. Die Konfigurierbarkeit ist bewusst
 * auf die Strategy selbst begrenzt, damit spaetere Versionen oder Balancing-
 * Experimente die Aufrufer nicht auf eine konkrete Formel festnageln.
 */
class RiskLikeBattleResolverV1(
    private val config: RiskLikeBattleResolverV1Config = RiskLikeBattleResolverV1Config(),
) : BattleResolver {
    override fun resolve(
        attackTroops: Int,
        defendTroops: Int,
        requestedAttackDice: Int?,
        rng: DeterministicRng,
    ): BattleOutcome {
        require(attackTroops >= 2) {
            "BattleResolver benötigt mindestens 2 Angriffstruppen, war aber $attackTroops."
        }
        require(defendTroops >= 1) {
            "BattleResolver benötigt mindestens 1 Verteidigungstruppe, war aber $defendTroops."
        }
        require(requestedAttackDice == null || requestedAttackDice >= 1) {
            "BattleResolver.requestedAttackDice muss mindestens 1 sein, war aber " +
                "$requestedAttackDice."
        }

        val requestedDice = requestedAttackDice ?: config.maxAttackDice
        val attackDice = minOf(requestedDice, config.maxAttackDice, attackTroops - 1)
        val defendDice = minOf(config.maxDefendDice, defendTroops)

        val attackerRawRolls =
            List(attackDice) { rng.nextInt(config.dieSides) + 1 }
        val defenderRawRolls =
            List(defendDice) { rng.nextInt(config.dieSides) + 1 }

        val attackerRolls = attackerRawRolls.sortedDescending()
        val defenderRolls = defenderRawRolls.sortedDescending()
        val comparisons = minOf(attackDice, defendDice)

        var attackerLosses = 0
        var defenderLosses = 0

        repeat(comparisons) { index ->
            if (attackerRolls[index] > defenderRolls[index]) {
                defenderLosses += 1
            } else {
                attackerLosses += 1
            }
        }

        val attackerRemaining = attackTroops - attackerLosses
        val defenderRemaining = defendTroops - defenderLosses

        return BattleOutcome(
            attackerRemaining = attackerRemaining,
            defenderRemaining = defenderRemaining,
            rngTrace = attackerRawRolls + defenderRawRolls,
            attackDice = attackDice,
            defendDice = defendDice,
            attackerRolls = attackerRolls,
            defenderRolls = defenderRolls,
            attackerLosses = attackerLosses,
            defenderLosses = defenderLosses,
            minOccupyingTroops =
                if (defenderRemaining == 0) {
                    attackDice
                } else {
                    null
                },
        )
    }
}

data class RiskLikeBattleResolverV1Config(
    val maxAttackDice: Int = 3,
    val maxDefendDice: Int = 2,
    val dieSides: Int = 6,
) {
    init {
        require(maxAttackDice >= 1) {
            "RiskLikeBattleResolverV1Config.maxAttackDice muss mindestens 1 sein."
        }
        require(maxDefendDice >= 1) {
            "RiskLikeBattleResolverV1Config.maxDefendDice muss mindestens 1 sein."
        }
        require(dieSides >= 2) {
            "RiskLikeBattleResolverV1Config.dieSides muss mindestens 2 sein."
        }
    }
}
