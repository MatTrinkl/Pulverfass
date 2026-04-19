package at.aau.pulverfass.server.session

import at.aau.pulverfass.server.ids.DuplicateConnectionIdException
import at.aau.pulverfass.server.ids.SessionConnectionNotFoundException
import at.aau.pulverfass.server.ids.SessionTokenNotFoundException
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.SessionToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionManagerTest {
    @Test
    fun `createSession should bind token to connection`() {
        val manager =
            SessionManager {
                SessionToken("123e4567-e89b-12d3-a456-426614174010")
            }

        val session = manager.createSession(ConnectionId(1))

        assertEquals(
            SessionToken("123e4567-e89b-12d3-a456-426614174010"),
            session.sessionToken,
        )
        assertEquals(ConnectionId(1), session.connectionId)
        assertTrue(session.isConnected)
        assertEquals(session, manager.getByConnectionId(ConnectionId(1)))
    }

    @Test
    fun `detachConnection should keep session token but remove active binding`() {
        val manager =
            SessionManager {
                SessionToken("123e4567-e89b-12d3-a456-426614174011")
            }
        val session = manager.createSession(ConnectionId(2))

        val detached = manager.detachConnection(ConnectionId(2))

        assertEquals(session.sessionToken, detached?.sessionToken)
        assertNull(detached?.connectionId)
        assertFalse(detached?.isConnected ?: true)
        assertNull(manager.getByConnectionId(ConnectionId(2)))
        assertEquals(detached, manager.getByToken(session.sessionToken))
    }

    @Test
    fun `bindExisting should reattach existing session to new connection`() {
        val manager =
            SessionManager {
                SessionToken("123e4567-e89b-12d3-a456-426614174012")
            }
        val created = manager.createSession(ConnectionId(3))
        manager.detachConnection(ConnectionId(3))

        val rebound = manager.bindExisting(created.sessionToken, ConnectionId(4))

        assertEquals(created.sessionToken, rebound.sessionToken)
        assertEquals(ConnectionId(4), rebound.connectionId)
        assertEquals(rebound, manager.getByConnectionId(ConnectionId(4)))
    }

    @Test
    fun `createSession should reject duplicate connection binding`() {
        val manager =
            SessionManager {
                SessionToken("123e4567-e89b-12d3-a456-426614174013")
            }
        manager.createSession(ConnectionId(5))

        assertThrows(DuplicateConnectionIdException::class.java) {
            manager.createSession(ConnectionId(5))
        }
    }

    @Test
    fun `bindExisting should reject duplicate target connection and keep current binding`() {
        val tokens =
            listOf(
                SessionToken("123e4567-e89b-12d3-a456-426614174015"),
                SessionToken("123e4567-e89b-12d3-a456-426614174016"),
            ).iterator()
        val manager = SessionManager { tokens.next() }
        val first = manager.createSession(ConnectionId(6))
        val second = manager.createSession(ConnectionId(7))

        assertThrows(DuplicateConnectionIdException::class.java) {
            manager.bindExisting(first.sessionToken, ConnectionId(7))
        }

        assertEquals(first, manager.getByConnectionId(ConnectionId(6)))
        assertEquals(second, manager.getByConnectionId(ConnectionId(7)))
    }

    @Test
    fun `requireByConnectionId should throw for unknown connection`() {
        val manager = SessionManager()

        assertThrows(SessionConnectionNotFoundException::class.java) {
            manager.requireByConnectionId(ConnectionId(99))
        }
    }

    @Test
    fun `requireByToken should throw for unknown token`() {
        val manager = SessionManager()

        assertThrows(SessionTokenNotFoundException::class.java) {
            manager.requireByToken(SessionToken("123e4567-e89b-12d3-a456-426614174014"))
        }
    }
}
