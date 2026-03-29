package at.aau.pulverfass.server.ids

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.GameId
import at.aau.pulverfass.shared.ids.PlayerId
import java.util.concurrent.atomic.AtomicLong

/**
 * Zentrale Stelle zur Erzeugung aller IDs innerhalb eines laufenden Server-Prozesses.
 *
 * Die Factory verhindert doppelte IDs pro JVM-Laufzeit und bündelt die Vergabe
 * an einer zentralen Stelle.
 */
object IdFactory {
    private val nextPlayerValue = AtomicLong(0)
    private val nextEntityValue = AtomicLong(0)
    private val nextConnectionValue = AtomicLong(0)
    private val nextGameValue = AtomicLong(0)

    /**
     * Erzeugt eine neue eindeutige PlayerId.
     */
    fun nextPlayerId(): PlayerId = PlayerId(nextPlayerValue.incrementAndGet())

    /**
     * Erzeugt eine neue eindeutige EntityId.
     */
    fun nextEntityId(): EntityId = EntityId(nextEntityValue.incrementAndGet())

    /**
     * Erzeugt eine neue eindeutige ConnectionId.
     */
    fun nextConnectionId(): ConnectionId = ConnectionId(nextConnectionValue.incrementAndGet())

    /**
     * Erzeugt eine neue eindeutige GameId.
     */
    fun nextGameId(): GameId = GameId(nextGameValue.incrementAndGet())
}
