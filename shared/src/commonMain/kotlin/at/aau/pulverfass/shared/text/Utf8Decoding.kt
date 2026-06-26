package at.aau.pulverfass.shared.text

/**
 * Dekodiert Netzwerk- und Konfigurationsbytes strikt als UTF-8.
 *
 * Im Gegensatz zu `ByteArray.decodeToString()` ohne Flag werden fehlerhafte
 * Bytefolgen nicht stillschweigend durch Ersatzzeichen ersetzt, sondern lösen
 * eine [kotlin.text.CharacterCodingException] aus.
 */
internal fun ByteArray.decodeUtf8Strict(): String = decodeToString(throwOnInvalidSequence = true)
