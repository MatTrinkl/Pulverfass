package at.aau.pulverfass.shared.network.transport

import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Wird erzeugt, sobald eine technische Verbindung erfolgreich aufgebaut ist.
 */
data class Connected(
    override val connectionId: ConnectionId,
) : ConnectionBoundTransportEvent

/**
 * Wird erzeugt, wenn über eine Verbindung eine Binärnachricht empfangen wurde.
 *
 * Die Nutzdaten bleiben hier absichtlich roh, damit Dekodierung und
 * Protokollinterpretation in höheren Schichten stattfinden können.
 */
class BinaryMessageReceived(
    override val connectionId: ConnectionId,
    bytes: ByteArray,
) : ReceivedTransportEvent {
    private val rawBytes: ByteArray = bytes.copyOf()

    val bytes: ByteArray get() = rawBytes.copyOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BinaryMessageReceived

        if (connectionId != other.connectionId) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = connectionId.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

/**
 * Wird erzeugt, wenn eine bestehende Verbindung beendet wurde.
 */
data class Disconnected(
    override val connectionId: ConnectionId,
    val reason: String? = null,
) : ConnectionBoundTransportEvent

/**
 * Optionales technisches Fehlerereignis auf der Transportebene.
 *
 * `connectionId` kann fehlen, wenn ein Fehler vor der vollständigen Zuordnung
 * zu einer Verbindung auftritt.
 */
data class TransportError(
    override val connectionId: ConnectionId?,
    val cause: Throwable,
) : TransportEvent
