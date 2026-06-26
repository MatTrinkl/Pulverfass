package at.aau.pulverfass.shared.crypto

/**
 * Berechnet einen SHA-256-Digest über die Plattform-Kryptobibliothek.
 */
internal expect fun sha256(bytes: ByteArray): ByteArray
