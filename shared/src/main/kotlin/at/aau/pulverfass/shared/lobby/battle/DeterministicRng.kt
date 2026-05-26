package at.aau.pulverfass.shared.lobby.battle

/**
 * Kleines deterministisches RNG-Interface fuer serverautoritatives Gameplay.
 *
 * Implementierungen muessen bei gleicher Startkonfiguration und gleicher
 * Aufrufreihenfolge immer dieselben Werte liefern.
 */
interface DeterministicRng {
    /**
     * Liefert einen deterministischen Wert im Bereich `0 until until`.
     */
    fun nextInt(until: Int): Int

    /**
     * Liefert den aktuellen internen RNG-Zustand vor dem naechsten Draw.
     */
    fun snapshotState(): Long
}

/**
 * Hilfsfabrik fuer serverautoritatives Battle-RNG auf Basis des im GameState
 * persistierten RNG-Cursors.
 */
object BattleRngFactory {
    fun fromState(rngState: Long): DeterministicRng = SplitMix64DeterministicRng(rngState)
}
