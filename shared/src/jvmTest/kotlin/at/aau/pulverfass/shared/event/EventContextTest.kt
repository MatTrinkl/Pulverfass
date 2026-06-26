package at.aau.pulverfass.shared.event

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EventContextTest {
    @Test
    fun `should instantiate context and metadata consistently`() {
        val correlationId = CorrelationId("trace-123")
        val context =
            EventContext(
                connectionId = ConnectionId(11),
                playerId = PlayerId(7),
                occurredAtEpochMillis = 1_725_000_000_000,
                correlationId = correlationId,
            )

        assertEquals(ConnectionId(11), context.connectionId)
        assertEquals(PlayerId(7), context.playerId)
        assertEquals(1_725_000_000_000, context.occurredAtEpochMillis)
        assertEquals(correlationId, context.correlationId)
    }

    @Test
    fun `should allow missing connection id in event context`() {
        val context =
            EventContext(
                connectionId = null,
                playerId = PlayerId(2),
                occurredAtEpochMillis = 42,
                correlationId = null,
            )

        assertNull(context.connectionId)
        assertEquals(PlayerId(2), context.playerId)
    }

    @Test
    fun `should preserve correlation metadata for later logging`() {
        val correlationId = CorrelationId("corr-lobby-join-01")
        val original =
            EventContext(
                connectionId = ConnectionId(3),
                playerId = null,
                occurredAtEpochMillis = 99,
                correlationId = correlationId,
            )

        val forwarded =
            original.copy(
                playerId = PlayerId(5),
            )

        assertEquals(correlationId, forwarded.correlationId)
        assertEquals(ConnectionId(3), forwarded.connectionId)
        assertEquals(PlayerId(5), forwarded.playerId)
    }

    @Test
    fun `should reject blank correlation id and negative timestamps`() {
        assertThrows(IllegalArgumentException::class.java) {
            CorrelationId(" ")
        }

        assertThrows(IllegalArgumentException::class.java) {
            EventContext(
                occurredAtEpochMillis = -1,
            )
        }
    }
}
