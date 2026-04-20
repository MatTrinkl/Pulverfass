package at.aau.pulverfass.shared.ids

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SessionTokenTest {
    @Test
    fun `sessionToken accepts lowercase uuid strings`() {
        assertDoesNotThrow {
            SessionToken("123e4567-e89b-12d3-a456-426614174000")
        }
    }

    @Test
    fun `sessionToken rejects uppercase uuid strings`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                SessionToken("123E4567-E89B-12D3-A456-426614174000")
            }

        assertTrue(exception.message.orEmpty().contains("Kleinbuchstaben"))
    }

    @Test
    fun `sessionToken rejects malformed uuid strings with helpful message`() {
        val invalidValue = "123e4567e89b12d3a456426614174000"

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                SessionToken(invalidValue)
            }

        assertEquals(
            "SessionToken muss ein UUID-String in Kleinbuchstaben sein, war aber '$invalidValue'.",
            exception.message,
        )
    }
}
