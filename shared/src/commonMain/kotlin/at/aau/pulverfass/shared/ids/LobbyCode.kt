package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private val lobbyCodePattern = Regex("^\\d{4}$")

/**
 * Fachlicher Join-Code einer Lobby.
 *
 * Der Code ist die stabile Identität einer Lobby und besteht aus genau vier
 * Ziffern.
 */
@Serializable
@JvmInline
value class LobbyCode(val value: String) {
    init {
        require(lobbyCodePattern.matches(value)) {
            "LobbyCode muss genau 4 Ziffern enthalten, war aber '$value'."
        }
    }
}
