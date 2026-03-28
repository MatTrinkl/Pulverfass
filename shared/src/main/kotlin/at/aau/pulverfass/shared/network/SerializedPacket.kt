package at.aau.pulverfass.shared.network

/**
 * Das serialisierte Paket welches wirklich übertragen wird.
 *
 * @property headerBytes Der serialisierte Header.
 * @property payloadBytes Der serialisierte Payload.
 */
data class SerializedPacket(
    val headerBytes: ByteArray,
    val payloadBytes: ByteArray,
) {
    /**
     * Vergleicht zwei SerializedPacket Objekte.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SerializedPacket

        if (!headerBytes.contentEquals(other.headerBytes)) return false
        if (!payloadBytes.contentEquals(other.payloadBytes)) return false

        return true
    }

    /**
     * Gibt den HashCode dieses SerializedPacket zurück.
     */
    override fun hashCode(): Int {
        var result = headerBytes.contentHashCode()
        result = 31 * result + payloadBytes.contentHashCode()
        return result
    }
}
