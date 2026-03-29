package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

/**
 * Eindeutige ID für einen Spieler.
 *
 * Diese ID wird serverseitig vergeben und kann über das Netzwerk übertragen werden.
 */

@Serializable
@JvmInline
value class PlayerId(val value: Long) {
    init {
        require(value > 0) { "PlayerId muss positiv sein, war aber $value." }
    }
}
