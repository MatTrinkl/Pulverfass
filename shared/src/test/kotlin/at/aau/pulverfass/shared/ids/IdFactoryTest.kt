package at.aau.pulverfass.shared.ids

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class IdFactoryTest {
    @Test
    fun `player ids sollten eindeutig sein`() {
        val id1 = IdFactory.nextPlayerId()
        val id2 = IdFactory.nextPlayerId()

        assertNotEquals(id1, id2)
    }

    @Test
    fun `entity ids sollten eindeutig sein`() {
        val id1 = IdFactory.nextEntityId()
        val id2 = IdFactory.nextEntityId()

        assertNotEquals(id1, id2)
    }

    @Test
    fun `connection ids sollten eindeutig sein`() {
        val id1 = IdFactory.nextConnectionId()
        val id2 = IdFactory.nextConnectionId()

        assertNotEquals(id1, id2)
    }

    @Test
    fun `game ids sollten eindeutig sein`() {
        val id1 = IdFactory.nextGameId()
        val id2 = IdFactory.nextGameId()

        assertNotEquals(id1, id2)
    }
}
