package at.aau.pulverfass.shared.lobby

internal const val MAX_PLAYER_DISPLAY_NAME_LENGTH: Int = 32

/**
 * Erzwingt die gemeinsamen Invarianten fuer Anzeigenamen im Lobby-Kontext.
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
}
