package at.aau.pulverfass.shared.crypto

import java.security.MessageDigest

/**
 * Berechnet einen SHA-256-Digest. Kapselt die Plattform-API, damit der
 * Aufrufer-Code multiplatform-fähig bleibt.
 */
internal fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
