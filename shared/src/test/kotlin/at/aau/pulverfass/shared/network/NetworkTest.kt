package at.aau.pulverfass.shared.network

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class NetworkTest {
    @Test
    fun `should expose connected event data`() {
        val event = Network.Event.Connected(ConnectionId(1))

        assertEquals(ConnectionId(1), event.connectionId)
    }

    @Test
    fun `should expose message received event data`() {
        val payload = JoinLobbyRequest(LobbyCode("AB12"), "alice")
        val event = Network.Event.MessageReceived(ConnectionId(2), payload)

        assertEquals(ConnectionId(2), event.connectionId)
        assertEquals(payload, event.payload)
    }

    @Test
    fun `should expose disconnected event data`() {
        val event = Network.Event.Disconnected(ConnectionId(3), reason = "closed")

        assertEquals(ConnectionId(3), event.connectionId)
        assertEquals("closed", event.reason)
    }

    @Test
    fun `should expose error event data`() {
        val cause = IllegalStateException("boom")
        val event = Network.Event.Error(connectionId = null, cause = cause)

        assertNull(event.connectionId)
        assertSame(cause, event.cause)
    }
}
