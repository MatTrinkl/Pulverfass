package at.aau.pulverfass.shared.text

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Dekodiert Netzwerk- und Konfigurationsbytes strikt als UTF-8.
 *
 * Im Gegensatz zu `ByteArray.decodeToString()` werden fehlerhafte oder nicht
 * abbildbare Bytefolgen nicht stillschweigend durch Ersatzzeichen ersetzt.
 */
internal fun ByteArray.decodeUtf8Strict(): String =
    Charsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(this))
        .toString()
