package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer

/**
 * Zentrale, zustandsführende Datenstruktur einer Lobby.
 *
 * Der State ist vollständig unabhängig von Netzwerk, Routing und UI. Änderungen
 * sollen ausschließlich über eine definierte [LobbyEventReducer]-Implementierung
 * erfolgen. Die Struktur ist absichtlich klein gehalten, damit sie früh im
 * Projekt stabil eingesetzt und später erweitert werden kann.
 *
 * @property lobbyCode fachliche Identität der Lobby
 * @property lobbyOwner Spieler, der die Lobby erstellt hat und Administrationsrechte hat
 * @property players aktuell bekannte Spieler in stabiler Reihenfolge
 * @property playerDisplayNames Anzeigenamen der bekannten Spieler für die Lobby-UI
 * @property activePlayer aktuell aktiver Spieler, falls vorhanden
 * @property turnOrder aktuelle Zugreihenfolge
 * @property turnNumber laufende Zugnummer ab dem ersten abgeschlossenen Zug
 * @property status grober Lebenszyklus der Lobby
 * @property processedEventCount Anzahl bereits auf den State angewendeter Events
 * @property lastEventContext optionaler Kontext des zuletzt verarbeiteten Events
 * @property closedReason optionale Schließursache, falls die Lobby geschlossen wurde
 * @property lastInvalidActionReason zuletzt erkannte ungültige Aktion, falls vorhanden
 */
data class GameState(
    val lobbyCode: LobbyCode,
    val lobbyOwner: PlayerId? = null,
    val players: List<PlayerId> = emptyList(),
    val playerDisplayNames: Map<PlayerId, String> = players.associateWith { it.value.toString() },
    val activePlayer: PlayerId? = null,
    val turnOrder: List<PlayerId> = emptyList(),
    val turnNumber: Int = 0,
    val status: GameStatus = GameStatus.WAITING_FOR_PLAYERS,
    val processedEventCount: Long = 0,
    val lastEventContext: EventContext? = null,
    val closedReason: String? = null,
    val lastInvalidActionReason: String? = null,
) {
    init {
        require(turnNumber >= 0) {
            "GameState.turnNumber darf nicht negativ sein, war aber $turnNumber."
        }
        require(processedEventCount >= 0) {
            "GameState.processedEventCount darf nicht negativ sein, war aber $processedEventCount."
        }
        require(players == players.distinct()) {
            "GameState.players darf keine Duplikate enthalten."
        }
        require(playerDisplayNames.keys == players.toSet()) {
            "GameState.playerDisplayNames muss genau für alle Spieler Einträge enthalten."
        }
        require(turnOrder == turnOrder.distinct()) {
            "GameState.turnOrder darf keine Duplikate enthalten."
        }
        require(players.containsAll(turnOrder) && turnOrder.containsAll(players)) {
            "GameState.players und GameState.turnOrder müssen dieselben Spieler enthalten."
        }
        require(activePlayer == null || players.contains(activePlayer)) {
            "GameState.activePlayer muss Teil der Spielerliste sein."
        }
        require(lobbyOwner == null || players.contains(lobbyOwner)) {
            "GameState.lobbyOwner muss Teil der Spielerliste sein oder null."
        }
    }

    /**
     * Anzahl aktuell bekannter Spieler.
     */
    val playerCount: Int
        get() = players.size

    /**
     * Prüft, ob ein Spieler aktuell Teil des States ist.
     */
    fun hasPlayer(playerId: PlayerId): Boolean = players.contains(playerId)

    /**
     * Liefert den Anzeigenamen eines bekannten Spielers, falls vorhanden.
     */
    fun displayNameOf(playerId: PlayerId): String? = playerDisplayNames[playerId]

    /**
     * Liefert einen initialen State für eine einzelne Lobby.
     */
    companion object {
        /**
         * Erstellt den minimalen Anfangszustand für die Lobby mit [lobbyCode].
         */
        fun initial(lobbyCode: LobbyCode): GameState =
            GameState(
                lobbyCode = lobbyCode,
            )
    }

    /**
     * Liefert eine Kopie des States mit aktualisierten technischen Metadaten.
     *
     * Diese Methode ist für Reducer gedacht, nicht für fachliche Nutzung.
     */
    internal fun withMetadata(context: EventContext?): GameState =
        copy(
            processedEventCount = processedEventCount + 1,
            lastEventContext = context,
        )
}
