package at.aau.pulverfass.shared.lobby.battle

/**
 * Austauschbare Strategy fuer serverautoritatives Battle-Resolving.
 *
 * Ein Resolver loest genau eine Kampf-Runde auf. Clients duerfen die
 * Kampfaufloesung nicht selbst berechnen.
 */
interface BattleResolver {
    fun resolve(
        attackTroops: Int,
        defendTroops: Int,
        rng: DeterministicRng,
    ): BattleOutcome =
        resolve(
            attackTroops = attackTroops,
            defendTroops = defendTroops,
            requestedAttackDice = null,
            rng = rng,
        )

    fun resolve(
        attackTroops: Int,
        defendTroops: Int,
        requestedAttackDice: Int?,
        rng: DeterministicRng,
    ): BattleOutcome
}
