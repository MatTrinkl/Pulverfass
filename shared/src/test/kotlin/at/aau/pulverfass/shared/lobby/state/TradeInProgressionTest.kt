package at.aau.pulverfass.shared.lobby.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TradeInProgressionTest {
    @Test
    fun `trade in values for one to six are correct`() {
        assertEquals(2, TradeInProgression.tradeInValue(1))
        assertEquals(4, TradeInProgression.tradeInValue(2))
        assertEquals(6, TradeInProgression.tradeInValue(3))
        assertEquals(8, TradeInProgression.tradeInValue(4))
        assertEquals(10, TradeInProgression.tradeInValue(5))
        assertEquals(10, TradeInProgression.tradeInValue(6))
    }

    @Test
    fun `tradeInValue rejects zero and negative indices`() {
        assertThrows(IllegalArgumentException::class.java) {
            TradeInProgression.tradeInValue(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TradeInProgression.tradeInValue(-3)
        }
    }
}
