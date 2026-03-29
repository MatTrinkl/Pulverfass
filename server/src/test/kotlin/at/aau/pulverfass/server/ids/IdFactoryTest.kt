package at.aau.pulverfass.server.ids

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.GameId
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Tests für die zentrale IdFactory.
 *
 * Überprüft, dass alle generierten IDs eindeutig sind.
 */
class IdFactoryTest {
    @Test
    fun `player ids sollten eindeutig sein`() {
        val id1: PlayerId = IdFactory.nextPlayerId()
        val id2: PlayerId = IdFactory.nextPlayerId()

        assertNotEquals(id1, id2)
    }

    @Test
    fun `entity ids sollten eindeutig sein`() {
        val id1: EntityId = IdFactory.nextEntityId()
        val id2: EntityId = IdFactory.nextEntityId()

        assertNotEquals(id1, id2)
    }

    @Test
    fun `connection ids sollten eindeutig sein`() {
        val id1: ConnectionId = IdFactory.nextConnectionId()
        val id2: ConnectionId = IdFactory.nextConnectionId()

        assertNotEquals(id1, id2)
    }

    @Test
    fun `game ids sollten eindeutig sein`() {
        val id1: GameId = IdFactory.nextGameId()
        val id2: GameId = IdFactory.nextGameId()

        assertNotEquals(id1, id2)
    }
}
