package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.state.GameState

/**
 * Bewertet Map-Commands gegen den aktuellen State und erzeugt daraus
 * deterministische Domain-Events.
 */
interface MapCommandRuleService {
    /**
     * Übersetzt einen validen Command in die resultierende Event-Sequenz.
     *
     * @throws InvalidMapCommandException wenn der Command im aktuellen State
     * nicht erlaubt ist
     */
    fun createEvents(
        state: GameState,
        command: MapCommand,
    ): List<LobbyEvent>
}

/**
 * Fachlicher Fehler für illegale Map-Commands.
 */
class InvalidMapCommandException(
    message: String,
) : RuntimeException(message)
