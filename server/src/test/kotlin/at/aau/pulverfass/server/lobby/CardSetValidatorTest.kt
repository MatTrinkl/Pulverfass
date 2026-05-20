package at.aau.pulverfass.server.lobby

import at.aau.pulverfass.shared.lobby.state.CardType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardSetValidatorTest {
    @Test
    fun `should accept three of a kind`() {
        assertTrue(
            CardSetValidator.isValidSet(
                listOf(CardType.A, CardType.A, CardType.A),
            ),
        )
    }

    @Test
    fun `should accept one of each symbol`() {
        assertTrue(
            CardSetValidator.isValidSet(
                listOf(CardType.A, CardType.B, CardType.C),
            ),
        )
    }

    @Test
    fun `should allow one joker to complete a mixed set`() {
        assertTrue(
            CardSetValidator.isValidSet(
                listOf(CardType.A, CardType.B, CardType.JOKER),
            ),
        )
    }

    @Test
    fun `should allow two jokers to complete a valid set`() {
        assertTrue(
            CardSetValidator.isValidSet(
                listOf(CardType.A, CardType.JOKER, CardType.JOKER),
            ),
        )
    }

    @Test
    fun `should reject invalid non joker combinations`() {
        assertFalse(
            CardSetValidator.isValidSet(
                listOf(CardType.A, CardType.A, CardType.B),
            ),
        )
    }

    @Test
    fun `should reject anything other than exactly three cards`() {
        assertFalse(CardSetValidator.isValidSet(emptyList()))
        assertFalse(
            CardSetValidator.isValidSet(
                listOf(CardType.A, CardType.B),
            ),
        )
        assertFalse(
            CardSetValidator.isValidSet(
                listOf(CardType.A, CardType.B, CardType.C, CardType.JOKER),
            ),
        )
    }
}
