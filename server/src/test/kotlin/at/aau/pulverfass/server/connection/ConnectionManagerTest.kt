package at.aau.pulverfass.server.connection

import at.aau.pulverfass.server.ids.ConnectionNotFoundException
import at.aau.pulverfass.server.ids.DuplicateConnectionIdException
import at.aau.pulverfass.shared.ids.ConnectionId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectionManagerTest {
    @Test
    fun `register speichert connection fuer spaeteres lookup`() {
        val manager = ConnectionManager()
        val connection = FakeConnection(ConnectionId(1))

        manager.register(connection)

        assertSame(connection, manager.get(ConnectionId(1)))
        assertTrue(manager.contains(ConnectionId(1)))
    }

    @Test
    fun `lookup liefert registrierte connection`() {
        val manager = ConnectionManager()
        val first = FakeConnection(ConnectionId(1))
        val second = FakeConnection(ConnectionId(2))
        manager.register(first)
        manager.register(second)

        val result = manager.require(ConnectionId(2))

        assertSame(second, result)
    }

    @Test
    fun `doppelte registrierung wird verhindert`() {
        val manager = ConnectionManager()
        manager.register(FakeConnection(ConnectionId(3)))

        assertThrows(DuplicateConnectionIdException::class.java) {
            manager.register(FakeConnection(ConnectionId(3)))
        }
    }

    @Test
    fun `remove entfernt connection aus registry`() {
        val manager = ConnectionManager()
        val connection = FakeConnection(ConnectionId(4))
        manager.register(connection)

        val removed = manager.remove(ConnectionId(4))

        assertSame(connection, removed)
        assertNull(manager.get(ConnectionId(4)))
    }

    @Test
    fun `broadcast erreicht alle aktiven connections`() =
        runBlocking {
            val manager = ConnectionManager()
            val first = FakeConnection(ConnectionId(10))
            val second = FakeConnection(ConnectionId(20))
            val payload = byteArrayOf(1, 2, 3)
            manager.register(first)
            manager.register(second)

            manager.broadcast(payload)

            assertEquals(1, first.sentPayloads.size)
            assertEquals(1, second.sentPayloads.size)
            assertArrayEquals(payload, first.sentPayloads.single())
            assertArrayEquals(payload, second.sentPayloads.single())
        }

    @Test
    fun `sendMany bedient jede connection id nur einmal`() =
        runBlocking {
            val manager = ConnectionManager()
            val connection = FakeConnection(ConnectionId(30))
            manager.register(connection)

            manager.sendMany(listOf(ConnectionId(30), ConnectionId(30)), byteArrayOf(9, 8, 7))

            assertEquals(1, connection.sentPayloads.size)
            assertArrayEquals(byteArrayOf(9, 8, 7), connection.sentPayloads.single())
        }

    @Test
    fun `require wirft bei unbekannter connection`() {
        val manager = ConnectionManager()

        val exception =
            assertThrows(ConnectionNotFoundException::class.java) {
                manager.require(ConnectionId(99))
            }

        assertEquals(ConnectionId(99), exception.connectionId)
    }
}

private class FakeConnection(
    override val connectionId: ConnectionId,
) : Connection {
    val sentPayloads = mutableListOf<ByteArray>()

    override suspend fun send(bytes: ByteArray) {
        sentPayloads += bytes.copyOf()
    }
}
