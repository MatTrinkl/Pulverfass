package at.aau.pulverfass.shared.network

/**
 * Das serialisierte Paket welches wirklich übertragen wird.
 *
 * @property headerBytes Der serialisierte Header.
 * @property payloadBytes Der serialisierte Payload.
 */
class SerializedPacket(
    headerBytes: ByteArray,
    payloadBytes: ByteArray,
) {
    private val _headerBytes: ByteArray = headerBytes.copyOf()
    private val _payloadBytes: ByteArray = payloadBytes.copyOf()

    val headerBytes: ByteArray get() = _headerBytes.copyOf()
    val payloadBytes: ByteArray get() = _payloadBytes.copyOf()

    /**
     * Vergleicht zwei SerializedPacket Objekte.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SerializedPacket

        if (!_headerBytes.contentEquals(other._headerBytes)) return false
        if (!_payloadBytes.contentEquals(other._payloadBytes)) return false

        return true
    }

    /**
     * Gibt den HashCode dieses SerializedPacket zurück.
     */
    override fun hashCode(): Int {
        var result = _headerBytes.contentHashCode()
        result = 31 * result + _payloadBytes.contentHashCode()
        return result
    }
}
