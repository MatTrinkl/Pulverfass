package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

/**
 * Eindeutige ID für eine Entity im Spiel.
 *
 * Diese ID wird serverseitig vergeben und kann über das Netzwerk übertragen werden.
 */

@Serializable
@JvmInline
value class EntityId(val value: Long) {
    init {
        require(value > 0) { "EntityId muss positiv sein, war aber $value." }
    }
}
