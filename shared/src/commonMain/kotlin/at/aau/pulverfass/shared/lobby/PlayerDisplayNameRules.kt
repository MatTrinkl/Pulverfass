package at.aau.pulverfass.shared.lobby

const val MAX_PLAYER_DISPLAY_NAME_LENGTH: Int = 8
const val FALLBACK_PLAYER_DISPLAY_NAME: String = "SPIELER"

/**
 * Normalisiert einen eingegebenen Spielernamen für Lobby und Spiel.
 *
 * Erlaubt sind ASCII-Buchstaben von A bis Z. Andere Zeichen werden verworfen,
 * die Länge wird begrenzt und die Anzeige wird einheitlich großgeschrieben.
 *
 * @param value roher Eingabewert aus UI, Persistenz oder Netzwerk
 * @return normalisierter Anzeigename
 */
fun normalizePlayerDisplayName(value: String): String =
    value
        .asSequence()
        .filter(::isAllowedPlayerDisplayNameCharacter)
        .take(MAX_PLAYER_DISPLAY_NAME_LENGTH)
        .joinToString(separator = "")
        .uppercase()

/**
 * Normalisiert einen Anzeigenamen und liefert bei leeren Alt-Daten einen
 * gültigen Ersatzwert.
 *
 * @param value roher Anzeigename aus Netzwerk, Persistenz oder State
 * @return gültiger Anzeigename mit maximal acht ASCII-Großbuchstaben
 */
fun normalizePlayerDisplayNameOrFallback(value: String): String =
    normalizePlayerDisplayName(value).ifBlank { FALLBACK_PLAYER_DISPLAY_NAME }

/**
 * Erzwingt die gemeinsamen Invarianten für Anzeigenamen im Lobby-Kontext.
 */
internal fun requireValidPlayerDisplayName(
    value: String,
    fieldName: String = "playerDisplayName",
) {
    require(value.isNotBlank()) {
        "$fieldName darf nicht leer oder nur Whitespace sein."
    }
    require(value.length <= MAX_PLAYER_DISPLAY_NAME_LENGTH) {
        "$fieldName darf hoechstens $MAX_PLAYER_DISPLAY_NAME_LENGTH Zeichen lang sein."
    }
    require(value.all(::isAllowedPlayerDisplayNameCharacter)) {
        "$fieldName darf nur die Buchstaben A bis Z enthalten."
    }
}

private fun isAllowedPlayerDisplayNameCharacter(character: Char): Boolean =
    character in 'A'..'Z' ||
        character in 'a'..'z'
