package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

/**
 * Eindeutige ID für eine technische Verbindung.
 *
 * Diese ID wird serverseitig vergeben und kann über das Netzwerk übertragen werden.
 */

@Serializable
@JvmInline
value class ConnectionId(val value: Long) {
    init {
        require(value > 0) { "ConnectionId muss positiv sein, war aber $value." }
    }
}
