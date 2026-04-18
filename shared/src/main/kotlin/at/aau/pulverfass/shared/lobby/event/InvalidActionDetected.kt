package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Signalisiert, dass innerhalb der Lobby-Verarbeitung eine ungültige Aktion
 * erkannt wurde.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId optionaler Spielerbezug, falls die Aktion zugeordnet werden konnte
 * @property reason fachliche oder technische Fehlerbeschreibung
 */
data class InvalidActionDetected(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId? = null,
    val reason: String,
) : InternalLobbyEvent {
    init {
        require(reason.isNotBlank()) {
            "InvalidActionDetected.reason darf nicht leer sein."
        }
    }
}
