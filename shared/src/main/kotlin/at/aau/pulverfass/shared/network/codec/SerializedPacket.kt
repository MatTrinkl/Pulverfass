package at.aau.pulverfass.shared.network.codec

/**
 * Technisches Paketmodell aus bereits serialisiertem Header und Payload.
 *
 * Diese Klasse repräsentiert die Grenze zwischen Nachrichten-Serialisierung und
 * binärem Wire-Format. Sie enthält nur Bytes und keine fachlichen Typen.
 *
 * @property headerBytes serialisierte Header-Daten
 * @property payloadBytes serialisierte Payload-Daten
 */
class SerializedPacket(
    headerBytes: ByteArray,
    payloadBytes: ByteArray,
) {
    init {
        if (headerBytes.isEmpty()) {
            throw EmptyHeaderException()
        }
    }

    private val rawHeaderBytes: ByteArray = headerBytes.copyOf()
    private val rawPayloadBytes: ByteArray = payloadBytes.copyOf()

    /**
     * Liefert eine defensive Kopie der Header-Bytes.
     */
    val headerBytes: ByteArray
        get() = rawHeaderBytes.copyOf()

    /**
     * Liefert eine defensive Kopie der Payload-Bytes.
     */
    val payloadBytes: ByteArray
        get() = rawPayloadBytes.copyOf()

    /**
     * Vergleicht zwei Pakete inhaltlich über Header- und Payload-Bytes.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SerializedPacket

        if (!rawHeaderBytes.contentEquals(other.rawHeaderBytes)) return false
        if (!rawPayloadBytes.contentEquals(other.rawPayloadBytes)) return false

        return true
    }

    /**
     * Berechnet einen inhaltsbasierten Hashcode über Header- und Payload-Bytes.
     */
    override fun hashCode(): Int {
        var result = rawHeaderBytes.contentHashCode()
        result = 31 * result + rawPayloadBytes.contentHashCode()
        return result
    }
}
