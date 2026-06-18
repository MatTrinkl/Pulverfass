package at.aau.pulverfass.shared.lobby

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerDisplayNameRulesTest {
    @Test
    fun `normalizePlayerDisplayName uppercases and limits ascii letters`() {
        assertEquals("ABCDEFGH", normalizePlayerDisplayName("abcdefghijkl"))
    }

    @Test
    fun `normalizePlayerDisplayName removes digits umlauts emoji and symbols`() {
        assertEquals("ABCXYZ", normalizePlayerDisplayName("a1 b_😊cäöüxYz"))
    }

    @Test
    fun `normalizePlayerDisplayNameOrFallback replaces empty normalized names`() {
        assertEquals(FALLBACK_PLAYER_DISPLAY_NAME, normalizePlayerDisplayNameOrFallback("123😊"))
    }

    @Test
    fun `requireValidPlayerDisplayName accepts up to eight latin letters`() {
        requireValidPlayerDisplayName("ALICE")
        requireValidPlayerDisplayName("ALICEBOB")
        requireValidPlayerDisplayName("alice")
    }

    @Test
    fun `requireValidPlayerDisplayName rejects invalid player display names`() {
        val invalidNames = listOf("", "   ", "ALICEBOB!", "ALICEBOBB", "ALICE1", "ÄLICE")

        invalidNames.forEach { invalidName ->
            val exception =
                assertThrows(IllegalArgumentException::class.java) {
                    requireValidPlayerDisplayName(invalidName)
                }

            assertTrue(exception.message.orEmpty().contains("playerDisplayName"))
        }
    }
}
