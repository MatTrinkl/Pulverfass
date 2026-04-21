package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.network.exception.UnknownMessageTypeIdException
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
        assertTrue(MessageType.entries.contains(MessageType.CONNECTION_REQUEST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_CREATE_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_ENDED_BROADCAST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_CREATE_ERROR_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_JOIN_ERROR_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_LEAVE_REQUEST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_LEAVE_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_PLAYER_LEFT_BROADCAST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_MAP_GET_REQUEST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_MAP_GET_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_MAP_GET_ERROR_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_TERRITORY_OWNER_CHANGED_BROADCAST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_TERRITORY_TROOPS_CHANGED_BROADCAST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_TURN_ADVANCE_REQUEST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_TURN_ADVANCE_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_TURN_ADVANCE_ERROR_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_TURN_STATE_UPDATED_BROADCAST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_TURN_STATE_GET_REQUEST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_TURN_STATE_GET_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_TURN_STATE_GET_ERROR_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_START_PLAYER_SET_REQUEST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_START_PLAYER_SET_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_START_PLAYER_SET_ERROR_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_GAME_STATE_DELTA_BROADCAST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_PHASE_BOUNDARY_BROADCAST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_GAME_STATE_SNAPSHOT_BROADCAST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_GAME_STATE_PRIVATE_GET_REQUEST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_GAME_STATE_PRIVATE_GET_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_GAME_STATE_PRIVATE_GET_ERROR_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_GAME_STATE_CATCH_UP_REQUEST))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_GAME_STATE_CATCH_UP_RESPONSE))
        assertTrue(MessageType.entries.contains(MessageType.LOBBY_GAME_STATE_CATCH_UP_ERROR_RESPONSE))
    }
}
