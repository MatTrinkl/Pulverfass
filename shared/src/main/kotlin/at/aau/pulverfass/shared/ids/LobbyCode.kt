package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

private val lobbyCodePattern = Regex("^[A-Z0-9]{4}$")

/**
 * Fachlicher Join-Code einer Lobby.
 *
 * Der Code ist die stabile Identität einer Lobby und besteht aus genau vier
 * alphanumerischen Zeichen in Großschreibung.
 */
@Serializable
@JvmInline
value class LobbyCode(val value: String) {
    init {
        require(lobbyCodePattern.matches(value)) {
            "LobbyCode muss genau 4 Zeichen lang und alphanumerisch " +
                "in Großschreibung sein, war aber '$value'."
        }
    }
}
