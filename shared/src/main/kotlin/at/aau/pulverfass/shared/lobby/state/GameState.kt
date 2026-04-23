package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition

/**
 * Zentrale, zustandsführende Datenstruktur einer Lobby.
 *
 * Der State ist vollständig unabhängig von Netzwerk, Routing und UI. Änderungen
 * sollen ausschließlich über den Lobby-Reducer erfolgen. Die Struktur ist
 * absichtlich klein gehalten, damit sie früh im Projekt stabil eingesetzt und
 * später erweitert werden kann.
 *
 * @property lobbyCode fachliche Identität der Lobby
 * @property lobbyOwner Spieler, der die Lobby erstellt hat und Administrationsrechte hat
 * @property players aktuell bekannte Spieler in stabiler Reihenfolge
 * @property playerDisplayNames Anzeigenamen der bekannten Spieler für die Lobby-UI
 * @property activePlayer Legacy-Alias auf den aktuell aktiven Spieler
 * @property configuredStartPlayerId konfigurierte Startspieler-Auswahl im Lobby-Setup
 * @property turnOrder aktuelle deterministische Spielerreihenfolge
 * @property turnNumber Legacy-Zähler für bestehende Call-Sites
 * @property turnState eindeutiger serverseitiger Turn-/Phasen-Zustand der Lobby
 * @property gameStarted signalisiert, ob das Spiel fachlich bereits gestartet wurde
 * @property status grober Lebenszyklus der Lobby
 * @property stateVersion server-authoritative, monotone Zustandsversion für Clients
 * @property processedEventCount Anzahl bereits auf den State angewendeter Events
 * @property lastEventContext optionaler Kontext des zuletzt verarbeiteten Events
 * @property closedReason optionale Schließursache, falls die Lobby geschlossen wurde
 * @property lastInvalidActionReason zuletzt erkannte ungültige Aktion, falls vorhanden
 * @property mapDefinition readonly Definition der Spielmap, falls bereits gesetzt
 * @property territoryStates mutierbarer Laufzeitzustand aller Territorien
 */
data class GameState(
    val lobbyCode: LobbyCode,
    val lobbyOwner: PlayerId? = null,
    val players: List<PlayerId> = emptyList(),
    val playerDisplayNames: Map<PlayerId, String> = players.associateWith { it.value.toString() },
    val activePlayer: PlayerId? = null,
    val configuredStartPlayerId: PlayerId? = null,
    val turnOrder: List<PlayerId> = emptyList(),
    val turnNumber: Int = 0,
    val turnState: TurnState? = null,
    val gameStarted: Boolean = false,
    val status: GameStatus = GameStatus.WAITING_FOR_PLAYERS,
    val stateVersion: Long = 0,
    val processedEventCount: Long = 0,
    val lastEventContext: EventContext? = null,
    val closedReason: String? = null,
    val lastInvalidActionReason: String? = null,
    val mapDefinition: MapDefinition? = null,
    val territoryStates: Map<TerritoryId, TerritoryState> = emptyMap(),
) {
    init {
        require(turnNumber >= 0) {
            "GameState.turnNumber darf nicht negativ sein, war aber $turnNumber."
        }
        require(stateVersion >= 0) {
            "GameState.stateVersion darf nicht negativ sein, war aber $stateVersion."
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
        require(configuredStartPlayerId == null || players.contains(configuredStartPlayerId)) {
            "GameState.configuredStartPlayerId muss Teil der Spielerliste sein oder null."
        }
        require(turnState == null || players.contains(turnState.activePlayerId)) {
            "GameState.turnState.activePlayerId muss Teil der Spielerliste sein."
        }
        require(turnState == null || players.contains(turnState.startPlayerId)) {
            "GameState.turnState.startPlayerId muss Teil der Spielerliste sein."
        }
        require(lobbyOwner == null || players.contains(lobbyOwner)) {
            "GameState.lobbyOwner muss Teil der Spielerliste sein oder null."
        }
        require(turnState == null || turnOrder.contains(turnState.activePlayerId)) {
            "GameState.turnState.activePlayerId muss Teil der TurnOrder sein."
        }
        require(turnState == null || turnOrder.contains(turnState.startPlayerId)) {
            "GameState.turnState.startPlayerId muss Teil der TurnOrder sein."
        }
        require(
            turnState == null || activePlayer == null || activePlayer == turnState.activePlayerId,
        ) {
            "GameState.activePlayer und GameState.turnState.activePlayerId müssen identisch sein."
        }
        require(
            turnState == null ||
                configuredStartPlayerId == null ||
                configuredStartPlayerId == turnState.startPlayerId,
        ) {
            "GameState.configuredStartPlayerId und " +
                "GameState.turnState.startPlayerId müssen identisch sein."
        }
        require((mapDefinition == null) == territoryStates.isEmpty()) {
            "GameState.mapDefinition und GameState.territoryStates müssen " +
                "gemeinsam gesetzt oder leer sein."
        }

        if (mapDefinition != null) {
            val expectedTerritoryIds = mapDefinition.territoriesById.keys
            require(territoryStates.keys == expectedTerritoryIds) {
                "GameState.territoryStates muss genau alle Territorien der MapDefinition enthalten."
            }
            territoryStates.forEach { (territoryId, territoryState) ->
                require(territoryState.territoryId == territoryId) {
                    "TerritoryState '$territoryId' muss dieselbe TerritoryId " +
                        "wie sein Map-Key besitzen."
                }
            }
        }
    }

    /**
     * Anzahl aktuell bekannter Spieler.
     */
    val playerCount: Int
        get() = players.size

    /**
     * Liefert den aufgelösten Turn-Zustand, inklusive Legacy-Fallback.
     */
    val resolvedTurnState: TurnState?
        get() = turnState ?: TurnStateMachine.fromLegacy(activePlayer, turnOrder, turnNumber)

    /**
     * Liefert die aktuell aktive Turn-Phase, falls ein TurnState vorhanden ist.
     */
    val activeTurnPhase: TurnPhase?
        get() = resolvedTurnState?.turnPhase

    /**
     * Liefert den aktuell konfigurierten Startspieler für das Lobby-Setup.
     */
    val setupStartPlayerId: PlayerId?
        get() = configuredStartPlayerId ?: resolvedTurnState?.startPlayerId

    /**
     * Prüft, ob ein Spieler aktuell Teil des States ist.
     */
    fun hasPlayer(playerId: PlayerId): Boolean = players.contains(playerId)

    /**
     * Liefert den Anzeigenamen eines bekannten Spielers, falls vorhanden.
     */
    fun displayNameOf(playerId: PlayerId): String? = playerDisplayNames[playerId]

    /**
     * Prüft, ob bereits eine Map-Definition im State vorhanden ist.
     */
    fun hasMap(): Boolean = mapDefinition != null

    /**
     * Liefert alle Laufzeit-Territorien in stabiler Map-Reihenfolge.
     */
    fun allTerritoryStates(): List<TerritoryState> =
        mapDefinition
            ?.territories
            ?.map { territory -> territoryStates.getValue(territory.territoryId) }
            ?: emptyList()

    /**
     * Liefert den Zustand eines Territoriums, falls vorhanden.
     */
    fun territoryStateOf(territoryId: TerritoryId): TerritoryState? = territoryStates[territoryId]

    /**
     * Liefert den Zustand eines Territoriums oder bricht mit klarer Fehlermeldung ab.
     */
    fun requireTerritoryState(territoryId: TerritoryId): TerritoryState =
        territoryStateOf(territoryId)
            ?: throw IllegalArgumentException(
                "Territory '$territoryId' ist im aktuellen GameState nicht vorhanden.",
            )

    /**
     * Liefert den Besitzer eines Territoriums, falls gesetzt.
     */
    fun territoryOwnerOf(territoryId: TerritoryId): PlayerId? =
        requireTerritoryState(territoryId).ownerId

    /**
     * Alias für Gameplay-Logik: Besitzer eines Territoriums.
     */
    fun ownerOf(territoryId: TerritoryId): PlayerId? = territoryOwnerOf(territoryId)

    /**
     * Liefert die aktuelle Truppenanzahl eines Territoriums.
     */
    fun troopCountOf(territoryId: TerritoryId): Int = requireTerritoryState(territoryId).troopCount

    /**
     * Alias für Gameplay-Logik: Truppenanzahl eines Territoriums.
     */
    fun troopsOn(territoryId: TerritoryId): Int = troopCountOf(territoryId)

    /**
     * Liefert die Nachbarschaften eines Territoriums direkt aus der Map-Definition.
     */
    fun adjacencyOf(territoryId: TerritoryId): List<TerritoryEdgeDefinition> =
        requireMapDefinition()
            .territoriesById[territoryId]
            ?.edges
            ?: throw IllegalArgumentException(
                "Territory '$territoryId' ist in der MapDefinition nicht vorhanden.",
            )

    /**
     * Liefert die Nachbar-Territory-IDs eines Territoriums in stabiler Konfigurationsreihenfolge.
     */
    fun neighbors(territoryId: TerritoryId): List<TerritoryId> =
        adjacencyOf(territoryId).map(TerritoryEdgeDefinition::targetId)

    /**
     * Liefert die benachbarten Territory-States eines Territoriums.
     */
    fun adjacentTerritories(territoryId: TerritoryId): List<TerritoryState> =
        neighbors(territoryId).map(::requireTerritoryState)

    /**
     * Prüft, ob zwei Territorien direkt benachbart sind.
     */
    fun isAdjacent(
        from: TerritoryId,
        to: TerritoryId,
    ): Boolean = neighbors(from).contains(to)

    /**
     * Liefert alle Territorien, die aktuell einem Spieler gehören.
     */
    fun territoriesOwnedBy(playerId: PlayerId): List<TerritoryState> =
        allTerritoryStates().filter { it.ownerId == playerId }

    /**
     * Liefert den Spieler, der einen Kontinent vollständig kontrolliert, sonst null.
     */
    fun continentOwner(continentId: ContinentId): PlayerId? {
        val continent =
            requireMapDefinition()
                .continentsById[continentId]
                ?: throw IllegalArgumentException(
                    "Continent '$continentId' ist in der MapDefinition nicht vorhanden.",
                )

        val owners =
            continent.territoryIds
                .map { territoryId -> requireTerritoryState(territoryId).ownerId }
                .toSet()

        return owners.singleOrNull()
    }

    /**
     * Prüft, ob ein Spieler einen Kontinent vollständig kontrolliert.
     */
    fun playerOwnsContinent(
        playerId: PlayerId,
        continentId: ContinentId,
    ): Boolean = continentOwner(continentId) == playerId

    /**
     * Liefert alle vollständig kontrollierten Kontinente eines Spielers.
     */
    fun continentsOwnedBy(playerId: PlayerId): List<ContinentId> =
        requireMapDefinition()
            .continents
            .filter { continent -> playerOwnsContinent(playerId, continent.continentId) }
            .map { continent -> continent.continentId }

    /**
     * Prüft, ob zwei Territorien über eine durchgehend eigene Verbindung erreichbar sind.
     *
     * Beide Endpunkte müssen dem Spieler gehören. Die Suche verwendet ausschließlich
     * die readonly Adjacency aus der MapDefinition.
     */
    fun isConnectedByOwnedPath(
        playerId: PlayerId,
        from: TerritoryId,
        to: TerritoryId,
    ): Boolean {
        requireTerritoryState(from)
        requireTerritoryState(to)

        if (ownerOf(from) != playerId || ownerOf(to) != playerId) {
            return false
        }
        if (from == to) {
            return true
        }

        val visited = linkedSetOf<TerritoryId>()
        val queue = ArrayDeque<TerritoryId>()
        queue.add(from)
        visited.add(from)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            neighbors(current)
                .asSequence()
                .filter { neighbor -> neighbor !in visited }
                .filter { neighbor -> ownerOf(neighbor) == playerId }
                .forEach { neighbor ->
                    if (neighbor == to) {
                        return true
                    }
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
        }

        return false
    }

    /**
     * Berechnet den Gesamtbonus eines Spielers aus vollständig kontrollierten Kontinenten.
     */
    fun bonusFor(playerId: PlayerId): Int =
        mapDefinition
            ?.continents
            ?.filter { continent -> continentOwner(continent.continentId) == playerId }
            ?.sumOf { continent -> continent.bonusValue }
            ?: 0

    /**
     * Liefert eine Kopie des States mit initialisierter Map und leeren Territoriumszuständen.
     */
    private fun withMapDefinition(
        mapDefinition: MapDefinition,
        initialTroopCount: Int = 0,
    ): GameState {
        require(initialTroopCount >= 0) {
            "initialTroopCount darf nicht negativ sein, war aber $initialTroopCount."
        }

        return copy(
            mapDefinition = mapDefinition,
            territoryStates =
                mapDefinition.territories.associate { territory ->
                    territory.territoryId to
                        TerritoryState(
                            territoryId = territory.territoryId,
                            troopCount = initialTroopCount,
                        )
                },
        )
    }

    /**
     * Liefert eine Kopie des States mit aktualisiertem Besitzer eines Territoriums.
     */
    internal fun withTerritoryOwner(
        territoryId: TerritoryId,
        ownerId: PlayerId?,
    ): GameState =
        withTerritoryState(
            requireTerritoryState(territoryId).copy(ownerId = ownerId),
        )

    /**
     * Liefert eine Kopie des States mit aktualisierter Truppenanzahl eines Territoriums.
     */
    internal fun withTerritoryTroops(
        territoryId: TerritoryId,
        troopCount: Int,
    ): GameState =
        withTerritoryState(
            requireTerritoryState(territoryId).copy(troopCount = troopCount),
        )

    /**
     * Ersetzt genau einen TerritoryState innerhalb des States.
     */
    internal fun withTerritoryState(territoryState: TerritoryState): GameState {
        requireTerritoryState(territoryState.territoryId)

        return copy(
            territoryStates = territoryStates + (territoryState.territoryId to territoryState),
        )
    }

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

        /**
         * Erstellt einen initialen State mit geladener Map und vorbereiteten Territory-Entities.
         */
        fun initial(
            lobbyCode: LobbyCode,
            mapDefinition: MapDefinition,
            players: List<PlayerId> = emptyList(),
            playerDisplayNames: Map<PlayerId, String> =
                players.associateWith { it.value.toString() },
        ): GameState {
            val turnOrder = TurnOrderPolicy.normalize(players)
            val turnState = TurnStateMachine.prepareSetupState(turnOrder)

            return GameState(
                lobbyCode = lobbyCode,
                players = players,
                playerDisplayNames = playerDisplayNames,
                activePlayer = turnState?.activePlayerId,
                configuredStartPlayerId = turnState?.startPlayerId,
                turnOrder = turnOrder,
                turnNumber = turnState?.turnCount ?: 0,
                turnState = turnState,
                lobbyOwner = players.firstOrNull(),
            ).withMapDefinition(mapDefinition)
        }
    }

    /**
     * Liefert eine Kopie des States mit aktualisierten technischen Metadaten.
     *
     * Diese Methode ist für Reducer gedacht, nicht für fachliche Nutzung.
     */
    internal fun withMetadata(context: EventContext?): GameState =
        copy(
            stateVersion = stateVersion + 1,
            processedEventCount = processedEventCount + 1,
            lastEventContext = context,
        )

    private fun requireMapDefinition(): MapDefinition =
        mapDefinition
            ?: throw IllegalStateException(
                "GameState enthält noch keine MapDefinition.",
            )
}
