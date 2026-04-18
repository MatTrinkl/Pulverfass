package at.aau.pulverfass.shared.network.transport

import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Signalisiert, dass über eine Verbindung eine Binärnachricht empfangen wurde.
 *
 * Die Nutzdaten bleiben hier absichtlich roh, damit Dekodierung und
 * Protokollinterpretation in höheren Schichten stattfinden können.
 */
class BinaryMessageReceived(
    override val connectionId: ConnectionId,
    bytes: ByteArray,
) : ReceivedTransportEvent {
    private val rawBytes: ByteArray = bytes.copyOf()

    /**
     * Liefert eine defensive Kopie der empfangenen Nutzdaten.
     */
    val bytes: ByteArray
        get() = rawBytes.copyOf()

    /**
     * Vergleicht zwei Ereignisse inhaltlich über Verbindung und Byte-Inhalt.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BinaryMessageReceived

        if (connectionId != other.connectionId) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false

        return true
    }

    /**
     * Berechnet einen inhaltsbasierten Hashcode über Verbindung und Byte-Inhalt.
     */
    override fun hashCode(): Int {
        var result = connectionId.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}
