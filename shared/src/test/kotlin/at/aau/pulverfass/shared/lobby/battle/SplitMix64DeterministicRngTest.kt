package at.aau.pulverfass.shared.lobby.battle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SplitMix64DeterministicRngTest {
    @Test
    fun `rng progression can resume from persisted state`() {
        val rng = SplitMix64DeterministicRng(1234L)

        val first = rng.nextInt(6)
        val persistedState = rng.snapshotState()
        val second = rng.nextInt(6)

        val resumed = SplitMix64DeterministicRng(persistedState)

        assertNotEquals(first, second)
        assertEquals(second, resumed.nextInt(6))
    }
}
