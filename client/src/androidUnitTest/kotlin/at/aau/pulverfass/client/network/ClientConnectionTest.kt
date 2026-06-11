package at.aau.pulverfass.client.network

import at.aau.pulverfass.shared.ids.ConnectionId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Prüft die feste Client-Connection-ID der Android-App.
 *
 * Der mobile Client unterstützt aktuell genau eine aktive WebSocket-Verbindung,
 * daher muss die ID stabil mit Sender und Empfänger übereinstimmen.
 */
class ClientConnectionTest {
    @Test
    fun `client connection id should be fixed to one`() {
        assertEquals(ConnectionId(1), CLIENT_CONNECTION_ID)
    }
}
