package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

/**
 * Eindeutige ID für ein Spiel.
 *
 * Diese ID wird serverseitig vergeben und kann über das Netzwerk übertragen werden.
 */

@Serializable
@JvmInline
value class GameId(val value: Long) {
    init {
        require(value > 0) { "GameId muss positiv sein, war aber $value." }
    }
}
