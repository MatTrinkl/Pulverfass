package at.aau.pulverfass.shared.lobby.reducer

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.state.GameState

/**
 * Wendet Lobby-Events kontrolliert auf einen [GameState] an.
 *
 * Der Reducer bildet die einzige vorgesehene Grenze für Zustandsänderungen im
 * Lobby-Layer. Er hält damit Mutationslogik, Validierung und Fehlerbehandlung
 * zentral an einer Stelle.
 */
interface LobbyEventReducer {
    /**
     * Wendet ein Event deterministisch auf den übergebenen State an.
     *
     * @param state aktueller Zustand der betroffenen Lobby
     * @param event anzuwendendes Lobby-Event
     * @param context optionale technische Event-Metadaten
     * @return neuer, aus dem Eingabestate abgeleiteter Zustand
     * @throws LobbyEventReductionException wenn das Event für den State nicht
     * gültig verarbeitet werden kann
     */
    fun apply(
        state: GameState,
        event: LobbyEvent,
        context: EventContext? = null,
    ): GameState
}
