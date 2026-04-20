package at.aau.pulverfass.app.network

import at.aau.pulverfass.shared.ids.ConnectionId
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientConnectionTest {
    @Test
    fun `client connection id should be fixed to one`() {
        assertEquals(ConnectionId(1), CLIENT_CONNECTION_ID)
    }
}
