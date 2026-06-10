package at.aau.pulverfass.shared.lobby.battle

/**
 * Ergebnis genau einer deterministisch aufgeloesten Kampf-Runde.
 *
 * `rngTrace` enthaelt die verbrauchten Wuerfelwerte in Ziehreihenfolge:
 * zuerst alle Angriffswuerfel, danach alle Verteidigungswuerfel.
 */
data class BattleOutcome(
    val attackerRemaining: Int,
    val defenderRemaining: Int,
    val rngTrace: List<Int>,
    val attackDice: Int,
    val defendDice: Int,
    val attackerRolls: List<Int>,
    val defenderRolls: List<Int>,
    val attackerLosses: Int,
    val defenderLosses: Int,
    val minOccupyingTroops: Int? = null,
) {
    val capture: Boolean
        get() = defenderRemaining == 0
}
