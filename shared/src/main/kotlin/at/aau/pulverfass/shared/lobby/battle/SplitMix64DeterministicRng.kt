package at.aau.pulverfass.shared.lobby.battle

/**
 * Deterministische SplitMix64-Implementierung fuer Replay-, Persistenz- und
 * Reconnect-stabile Gameplay-Aufloesung.
 */
class SplitMix64DeterministicRng(
    seed: Long,
) : DeterministicRng {
    private var state: ULong = seed.toULong()

    override fun nextInt(until: Int): Int {
        require(until > 0) {
            "DeterministicRng.nextInt benötigt until > 0, war aber $until."
        }

        state += GAMMA
        var z = state
        z = (z xor (z shr 30)) * MIX_1
        z = (z xor (z shr 27)) * MIX_2
        z = z xor (z shr 31)

        return (z % until.toULong()).toInt()
    }

    override fun snapshotState(): Long = state.toLong()

    private companion object {
        val GAMMA = 0x9E3779B97F4A7C15uL
        val MIX_1 = 0xBF58476D1CE4E5B9uL
        val MIX_2 = 0x94D049BB133111EBuL
    }
}
