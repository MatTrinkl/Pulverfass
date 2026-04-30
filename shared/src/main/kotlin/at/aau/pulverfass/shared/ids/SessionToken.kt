package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

private val sessionTokenPattern =
    Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")

/**
 * Stabiler technischer Session-Token für eine Client-Session über mehrere
 * Verbindungen hinweg.
 *
 * Das Format folgt aktuell einem UUID-String in Kleinbuchstaben.
 */
@Serializable
@JvmInline
value class SessionToken(val value: String) {
    init {
        require(sessionTokenPattern.matches(value)) {
            "SessionToken muss ein UUID-String in Kleinbuchstaben sein, war aber '$value'."
        }
    }
}
