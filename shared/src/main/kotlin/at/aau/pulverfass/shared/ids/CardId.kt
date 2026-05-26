package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

/**
 * Eindeutige ID einer Spielkarte.
 */
@Serializable
@JvmInline
value class CardId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "CardId darf nicht leer sein."
        }
    }
}
