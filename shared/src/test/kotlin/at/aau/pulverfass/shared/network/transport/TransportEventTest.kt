package at.aau.pulverfass.shared.network.transport

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.message.LoginRequest
import at.aau.pulverfass.shared.network.message.MessageType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransportEventTest {
    @Test
    fun `should instantiate transport events consistently`() {
        val connectionId = ConnectionId(42)
        val connected = Connected(connectionId)
        val binary = BinaryMessageReceived(connectionId, byteArrayOf(1, 2, 3))
        val loginRequest =
            LoginRequestReceived(
                connectionId = connectionId,
                payload = LoginRequest(username = "alice", password = "secret"),
            )
        val disconnected = Disconnected(connectionId, "client closed")
        val error = TransportError(connectionId, IllegalStateException("boom"))

        assertEquals(connectionId, connected.connectionId)
        assertArrayEquals(byteArrayOf(1, 2, 3), binary.bytes)
        assertEquals(connectionId, loginRequest.connectionId)
        assertEquals(MessageType.LOGIN_REQUEST, loginRequest.messageType)
        assertEquals("alice", loginRequest.payload.username)
        assertEquals(connectionId, disconnected.connectionId)
        assertEquals("client closed", disconnected.reason)
        assertEquals(connectionId, error.connectionId)
        assertEquals("boom", error.cause.message)
    }

    @Test
    fun `should pass connection id through all connection bound events`() {
        val connectionId = ConnectionId(7)
        val events =
            listOf<ConnectionBoundTransportEvent>(
                Connected(connectionId),
                BinaryMessageReceived(connectionId, byteArrayOf(9)),
                LoginRequestReceived(
                    connectionId = connectionId,
                    payload = LoginRequest(username = "bob", password = "hunter2"),
                ),
                Disconnected(connectionId),
            )

        events.forEach { event -> assertEquals(connectionId, event.connectionId) }
    }

    @Test
    fun `should defensively copy binary payload bytes`() {
        val source = byteArrayOf(1, 2, 3)
        val event = BinaryMessageReceived(ConnectionId(3), source)

        source[0] = 9
        val firstRead = event.bytes
        val secondRead = event.bytes
        firstRead[1] = 8

        assertArrayEquals(byteArrayOf(1, 2, 3), event.bytes)
        assertNotSame(firstRead, secondRead)
    }

    @Test
    fun `should compare binary messages by content`() {
        val first = BinaryMessageReceived(ConnectionId(4), byteArrayOf(1, 2))
        val second = BinaryMessageReceived(ConnectionId(4), byteArrayOf(1, 2))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `should keep binary message equal to itself`() {
        val event = BinaryMessageReceived(ConnectionId(6), byteArrayOf(7))

        assertSame(event, event)
        assertTrue(event == event)
    }

    @Test
    fun `should detect different binary message connection ids`() {
        val first = BinaryMessageReceived(ConnectionId(1), byteArrayOf(1, 2))
        val second = BinaryMessageReceived(ConnectionId(2), byteArrayOf(1, 2))

        assertNotEquals(first, second)
    }

    @Test
    fun `should detect different binary message bytes`() {
        val first = BinaryMessageReceived(ConnectionId(1), byteArrayOf(1, 2))
        val second = BinaryMessageReceived(ConnectionId(1), byteArrayOf(1, 9))

        assertNotEquals(first, second)
    }

    @Test
    fun `should reject binary message equality with null and other types`() {
        val event = BinaryMessageReceived(ConnectionId(8), byteArrayOf(1))

        assertFalse(event.equals(null))
        assertFalse(event.equals("not an event"))
    }

    @Test
    fun `should dispatch events through dispatcher interface`() {
        val received = mutableListOf<TransportEvent>()
        val dispatcher = TransportEventDispatcher { event -> received += event }
        val event = Connected(ConnectionId(5))

        dispatcher.dispatch(event)

        assertEquals(listOf(event), received)
    }

    @Test
    fun `should expose login request event as decoded message template`() {
        val event =
            LoginRequestReceived(
                connectionId = ConnectionId(11),
                payload = LoginRequest(username = "charlie", password = "topsecret"),
            )

        val decodedEvent: DecodedTransportEvent<LoginRequest> = event
        val receivedEvent: ReceivedTransportEvent = event
        val transportEvent: TransportEvent = event

        assertEquals(MessageType.LOGIN_REQUEST, decodedEvent.messageType)
        assertEquals("charlie", decodedEvent.payload.username)
        assertEquals("topsecret", decodedEvent.payload.password)
        assertEquals(ConnectionId(11), receivedEvent.connectionId)
        assertEquals(ConnectionId(11), transportEvent.connectionId)
    }

    @Test
    fun `should default disconnected reason to null`() {
        val event = Disconnected(ConnectionId(12))

        assertNull(event.reason)
    }

    @Test
    fun `should allow transport error without connection id`() {
        val event =
            TransportError(connectionId = null, cause = IllegalStateException("early failure"))

        assertNull(event.connectionId)
        assertEquals("early failure", event.cause.message)
    }
}
