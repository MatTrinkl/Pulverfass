package at.aau.pulverfass.shared.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageTypeTest {
    @Test
    fun `should map all valid ids to correct MessageType`() {
        MessageType.entries.forEach { type ->
            val result = MessageType.fromId(type.id)
            assertEquals(type, result)
        }
    }

    @Test
    fun `should throw exception for unknown id`() {
        val invalidId = 999

        val exception =
            assertThrows(UnknownMessageTypeIdException::class.java) {
                MessageType.fromId(invalidId)
            }

        assertEquals("Unknown MessageType id: 999", exception.message)
    }

    @Test
    fun `should throw exception for negative id`() {
        val invalidId = -1

        val exception =
            assertThrows(UnknownMessageTypeIdException::class.java) {
                MessageType.fromId(invalidId)
            }

        assertEquals("Unknown MessageType id: -1", exception.message)
    }

    @Test
    fun `should have unique ids for all message types`() {
        val ids = MessageType.entries.map { it.id }
        val uniqueIds = ids.toSet()

        assertEquals(ids.size, uniqueIds.size)
    }

    @Test
    fun `should contain expected enum values`() {
        assertTrue(MessageType.entries.contains(MessageType.LOGIN_REQUEST))
        assertTrue(MessageType.entries.contains(MessageType.GAME_CREATE_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.GAME_ENDED_BROADCAST))
    }
}
