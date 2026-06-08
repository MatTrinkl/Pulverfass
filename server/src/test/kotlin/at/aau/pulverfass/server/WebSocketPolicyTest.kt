package at.aau.pulverfass.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSocketPolicyTest {
    @Test
    fun `disconnect detection uses short positive ping and timeout intervals`() {
        assertEquals(5_000L, WebSocketPolicy.PING_PERIOD_MILLIS)
        assertEquals(5_000L, WebSocketPolicy.TIMEOUT_MILLIS)
        assertTrue(WebSocketPolicy.PING_PERIOD_MILLIS > 0)
        assertTrue(WebSocketPolicy.TIMEOUT_MILLIS > 0)
    }
}
