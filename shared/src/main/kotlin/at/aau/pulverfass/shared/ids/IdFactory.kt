package at.aau.pulverfass.shared.ids

import java.util.concurrent.atomic.AtomicLong

// Zentrale Stelle zur Erzeugung aller IDs.
// Verhindert doppelte IDs und sorgt für Konsistenz.
object IdFactory {
    private val counter = AtomicLong(0)

    fun nextPlayerId(): PlayerId {
        return PlayerId(counter.incrementAndGet())
    }

    fun nextEntityId(): EntityId {
        return EntityId(counter.incrementAndGet())
    }

    fun nextConnectionId(): ConnectionId {
        return ConnectionId(counter.incrementAndGet())
    }

    fun nextGameId(): GameId {
        return GameId(counter.incrementAndGet())
    }
}
