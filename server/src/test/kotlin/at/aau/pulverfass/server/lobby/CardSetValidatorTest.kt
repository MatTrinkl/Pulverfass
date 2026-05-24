package at.aau.pulverfass.server.lobby

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.lobby.state.CardState
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

    @Test
    fun `canMakeAnySet returns false for empty hand`() {
        assertFalse(CardSetValidator.canMakeAnySet(emptyList()))
    }

    @Test
    fun `canMakeAnySet returns false when fewer than three cards`() {
        assertFalse(
            CardSetValidator.canMakeAnySet(
                listOf(card("x", CardType.A), card("y", CardType.B)),
            ),
        )
    }

    @Test
    fun `canMakeAnySet returns false when no valid three-card combination exists`() {
        assertFalse(
            CardSetValidator.canMakeAnySet(
                listOf(
                    card("1", CardType.A),
                    card("2", CardType.A),
                    card("3", CardType.B),
                    card("4", CardType.B),
                ),
            ),
        )
    }

    @Test
    fun `canMakeAnySet returns true when at least one valid set exists in the hand`() {
        assertTrue(
            CardSetValidator.canMakeAnySet(
                listOf(
                    card("1", CardType.A),
                    card("2", CardType.A),
                    card("3", CardType.B),
                    card("4", CardType.B),
                    card("5", CardType.C),
                ),
            ),
        )
    }

    @Test
    fun `canMakeAnySet returns true when joker completes a set`() {
        assertTrue(
            CardSetValidator.canMakeAnySet(
                listOf(
                    card("1", CardType.A),
                    card("2", CardType.B),
                    card("3", CardType.JOKER),
                ),
            ),
        )
    }

    private fun card(
        id: String,
        type: CardType,
    ) = CardState(CardId(id), type)
}
