package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.command.MIN_SOURCE_TROOPS_FOR_ATTACK
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
 * @property gameRandomSeed optionaler, serverseitig gewählter Seed des aktuellen Spiels
 * @property gameRandomState optionaler, persistierter RNG-Cursor des aktuellen Spiels
 * @property lastEventContext optionaler Kontext des zuletzt verarbeiteten Events
 * @property closedReason optionale Schließursache, falls die Lobby geschlossen wurde
 * @property lastInvalidActionReason zuletzt erkannte ungültige Aktion, falls vorhanden
 * @property territoryCapturedThisTurn signalisiert, ob im aktuellen Zug mindestens ein Gebiet erobert wurde
 * @property usedCheatReinforcementBonusByPlayer Spieler, die ihren einmaligen
 * Schummel-Verstärkungsbonus bereits verwendet haben
 * @property mapDefinition readonly Definition der Spielmap, falls bereits gesetzt
 * @property territoryStates mutierbarer Laufzeitzustand aller Territorien
 * @property setupTroopsToPlaceByPlayer verbleibende Starttruppen pro Spieler nach der initialen Gebietsverteilung
 * @property pendingReinforcements verbleibender Verstärkungspool eines Spielers für Phase 1
 * @property handState autoritative Kartenhände aller Spieler
 * @property deckState Platzhalter für den serverseitigen Kartenstapel
 * @property discardPileState Platzhalter für den serverseitigen Ablagestapel
 * @property tradedInSetCount globaler Zähler aller bereits eingetauschten Kartensets dieser Lobby
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
    val gameRandomSeed: Long? = null,
    val gameRandomState: Long? = null,
    val lastEventContext: EventContext? = null,
    val closedReason: String? = null,
    val lastInvalidActionReason: String? = null,
    val fortifyUsedThisTurn: Boolean = false,
    val territoryCapturedThisTurn: Boolean = false,
    val usedCheatReinforcementBonusByPlayer: Set<PlayerId> = emptySet(),
    val mapDefinition: MapDefinition? = null,
    val territoryStates: Map<TerritoryId, TerritoryState> = emptyMap(),
    val setupTroopsToPlaceByPlayer: Map<PlayerId, Int> = players.associateWith { 0 },
    val pendingReinforcements: PendingReinforcements? = null,
    val handState: HandState = HandState(),
    val tradeRequiredOnNextReinforcementPhaseByPlayer: Map<PlayerId, Boolean> =
        players.associateWith { false },
    val deckState: DeckState = DeckState(),
    val discardPileState: DiscardPileState = DiscardPileState(),
    val tradedInSetCount: Int = 0,
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
        require(gameRandomSeed != null || gameRandomState == null) {
            "GameState.gameRandomState darf nur gesetzt sein, wenn auch gameRandomSeed gesetzt ist."
        }
        require(tradedInSetCount >= 0) {
            "GameState.tradedInSetCount darf nicht negativ sein, war aber $tradedInSetCount."
        }
        require(players == players.distinct()) {
            "GameState.players darf keine Duplikate enthalten."
        }
        require(playerDisplayNames.keys == players.toSet()) {
            "GameState.playerDisplayNames muss genau für alle Spieler Einträge enthalten."
        }
        require(setupTroopsToPlaceByPlayer.keys == players.toSet()) {
            "GameState.setupTroopsToPlaceByPlayer muss genau für alle Spieler Einträge enthalten."
        }
        require(setupTroopsToPlaceByPlayer.values.all { it >= 0 }) {
            "GameState.setupTroopsToPlaceByPlayer darf keine negativen Werte enthalten."
        }
        require(pendingReinforcements == null || players.contains(pendingReinforcements.playerId)) {
            "GameState.pendingReinforcements.playerId muss Teil der Spielerliste sein."
        }
        require(handState.cardsByPlayer.keys.all(players::contains)) {
            "GameState.handState darf nur Karten für bekannte Spieler enthalten."
        }
        require(tradeRequiredOnNextReinforcementPhaseByPlayer.keys == players.toSet()) {
            "GameState.tradeRequiredOnNextReinforcementPhaseByPlayer muss genau für alle " +
                "Spieler Einträge enthalten."
        }
        require(turnOrder == turnOrder.distinct()) {
            "GameState.turnOrder darf keine Duplikate enthalten."
        }
        require(turnOrder.all(players::contains)) {
            "GameState.turnOrder darf nur Spieler enthalten, die Teil der Lobby sind."
        }
        require(activePlayer == null || players.contains(activePlayer)) {
            "GameState.activePlayer muss Teil der Spielerliste sein."
        }
        require(usedCheatReinforcementBonusByPlayer.all(players::contains)) {
            "GameState.usedCheatReinforcementBonusByPlayer darf nur bekannte Spieler enthalten."
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
        require(allCardIds().distinct().size == allCardIds().size) {
            "GameState.handState, deckState und discardPileState dürfen " +
                "keine CardIds mehrfach enthalten."
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
     * Prüft, ob ein Spieler im laufenden Match eliminiert wurde.
     *
     * Eliminierte Spieler bleiben Teil der Lobby, besitzen aber keine Territorien
     * mehr und sind aus der aktiven TurnOrder entfernt.
     */
    fun isEliminated(playerId: PlayerId): Boolean =
        hasPlayer(playerId) &&
            hasStartedMatch() &&
            ownedTerritoryCount(playerId) == 0 &&
            !turnOrder.contains(playerId)

    /**
     * Prüft, ob ein Spieler nur noch als Zuschauer im Match verbleibt.
     */
    fun isSpectator(playerId: PlayerId): Boolean = isEliminated(playerId)

    /**
     * Liefert den Anzeigenamen eines bekannten Spielers, falls vorhanden.
     */
    fun displayNameOf(playerId: PlayerId): String? = playerDisplayNames[playerId]

    /**
     * Prüft, ob bereits eine Map-Definition im State vorhanden ist.
     */
    fun hasMap(): Boolean = mapDefinition != null

    /**
     * Liefert die verbleibenden Starttruppen eines Spielers.
     */
    fun setupTroopsToPlaceFor(playerId: PlayerId): Int =
        setupTroopsToPlaceByPlayer[playerId]
            ?: throw IllegalArgumentException(
                "Spieler '${playerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'.",
            )

    /**
     * Prüft, ob im Start-Setup noch Truppen platziert werden müssen.
     */
    fun hasPendingSetupTroops(): Boolean = setupTroopsToPlaceByPlayer.values.any { it > 0 }

    /**
     * Liefert den verbleibenden Verstärkungspool eines Spielers.
     */
    fun pendingReinforcementsFor(playerId: PlayerId): Int =
        pendingReinforcements
            ?.takeIf { it.playerId == playerId }
            ?.amount
            ?: 0

    /**
     * Prüft, ob aktuell noch ausstehende Verstärkungen vorhanden sind.
     */
    fun hasPendingReinforcements(): Boolean = (pendingReinforcements?.amount ?: 0) > 0

    /**
     * Prüft, ob ein Spieler in seiner nächsten Reinforcements-Phase zuerst Karten
     * traden muss.
     */
    fun tradeRequiredOnNextReinforcementPhaseFor(playerId: PlayerId): Boolean =
        tradeRequiredOnNextReinforcementPhaseByPlayer[playerId]
            ?: throw IllegalArgumentException(
                "Spieler '${playerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'.",
            )

    /**
     * Liefert die Kartenhand eines Spielers in stabiler Einfügereihenfolge.
     */
    fun handOf(playerId: PlayerId): List<CardState> = handState.cardsOf(playerId)

    /**
     * Liefert die aktuelle Handgröße eines Spielers.
     */
    fun handSizeOf(playerId: PlayerId): Int = handState.handSizeOf(playerId)

    /**
     * Prüft, ob ein Spieler eine konkrete Karte besitzt.
     */
    fun playerHasCard(
        playerId: PlayerId,
        cardId: CardId,
    ): Boolean = handState.contains(playerId, cardId)

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
     * Liefert die Anzahl aller aktuell vom Spieler kontrollierten Territorien.
     */
    fun ownedTerritoryCount(playerId: PlayerId): Int = territoriesOwnedBy(playerId).size

    /**
     * Prüft, ob ein Spieler von einem Territorium aus legal angreifen kann.
     */
    fun canAttackFrom(
        territoryId: TerritoryId,
        playerId: PlayerId,
    ): Boolean {
        require(hasPlayer(playerId)) {
            "Spieler '${playerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }
        if (!hasMap() || territoryStateOf(territoryId) == null) {
            return false
        }

        val source = requireTerritoryState(territoryId)
        if (source.ownerId != playerId || source.troopCount < MIN_SOURCE_TROOPS_FOR_ATTACK) {
            return false
        }

        return validAttackTargets(territoryId, playerId).isNotEmpty()
    }

    /**
     * Liefert alle legalen Angriffsziele eines Territoriums in stabiler Map-Reihenfolge.
     *
     * Ein Ziel mit fremdem Besitzer, aber null Truppen, ist kein kampffähiges
     * Ziel mehr. Solche Zustände können während Cleanup-/Reconnect-Flows
     * sichtbar sein und dürfen die leere Angriffsphase nicht blockieren.
     */
    fun validAttackTargets(
        fromTerritoryId: TerritoryId,
        playerId: PlayerId,
    ): List<TerritoryId> {
        require(hasPlayer(playerId)) {
            "Spieler '${playerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }
        if (!hasMap() || territoryStateOf(fromTerritoryId) == null) {
            return emptyList()
        }

        val source = requireTerritoryState(fromTerritoryId)
        if (source.ownerId != playerId || source.troopCount < MIN_SOURCE_TROOPS_FOR_ATTACK) {
            return emptyList()
        }

        return adjacentTerritories(fromTerritoryId)
            .filter { adjacent ->
                adjacent.ownerId != null &&
                    adjacent.ownerId != playerId &&
                    adjacent.troopCount > 0
            }.map(TerritoryState::territoryId)
    }

    /**
     * Prüft, ob der Spieler aktuell mindestens einen legalen Angriff ausführen kann.
     */
    fun hasAnyValidAttack(playerId: PlayerId): Boolean {
        require(hasPlayer(playerId)) {
            "Spieler '${playerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }
        if (!hasMap()) {
            return false
        }

        return territoriesOwnedBy(playerId).any { territoryState ->
            canAttackFrom(territoryState.territoryId, playerId)
        }
    }

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
     * Alias für die Abfrage, ob ein Spieler einen Kontinent vollständig kontrolliert.
     */
    fun ownsContinent(
        playerId: PlayerId,
        continentId: ContinentId,
    ): Boolean = playerOwnsContinent(playerId, continentId)

    /**
     * Liefert den konfigurierten Bonuswert eines Kontinents.
     */
    fun continentBonus(continentId: ContinentId): Int =
        requireMapDefinition()
            .continentsById[continentId]
            ?.bonusValue
            ?: throw IllegalArgumentException(
                "Continent '$continentId' ist in der MapDefinition nicht vorhanden.",
            )

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

    fun canFortifyMove(
        playerId: PlayerId,
        from: TerritoryId,
        to: TerritoryId,
    ): Boolean = isConnectedByOwnedPath(playerId, from, to)

    /**
     * Liefert alle gültigen Fortify-Ziele für ein Ursprungsterritorium.
     *
     * Die Query ist rein lesend und eignet sich für UI-Hilfen oder
     * serverseitige Vorvalidierung. Sie respektiert denselben Turn-Kontext wie
     * ein echter Fortify-Move.
     */
    fun validFortifyTargets(
        playerId: PlayerId,
        fromTerritoryId: TerritoryId,
    ): List<TerritoryId> {
        if (
            activePlayer != playerId ||
            activeTurnPhase != TurnPhase.FORTIFY ||
            fortifyUsedThisTurn
        ) {
            return emptyList()
        }

        val sourceState = territoryStateOf(fromTerritoryId) ?: return emptyList()
        if (sourceState.ownerId != playerId || sourceState.troopCount <= 1) {
            return emptyList()
        }

        return territoriesOwnedBy(playerId)
            .asSequence()
            .map(TerritoryState::territoryId)
            .filterNot { territoryId -> territoryId == fromTerritoryId }
            .filter { territoryId -> canFortifyMove(playerId, fromTerritoryId, territoryId) }
            .toList()
    }

    /**
     * Berechnet den Gesamtbonus eines Spielers aus vollständig kontrollierten Kontinenten.
     */
    fun bonusFor(playerId: PlayerId): Int =
        mapDefinition
            ?.continents
            ?.filter { continent -> ownsContinent(playerId, continent.continentId) }
            ?.sumOf { continent -> continentBonus(continent.continentId) }
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
     * Setzt den ausstehenden Verstärkungspool auf einen absoluten Wert.
     */
    internal fun withPendingReinforcements(
        playerId: PlayerId,
        amount: Int,
    ): GameState =
        copy(
            pendingReinforcements = PendingReinforcements(playerId = playerId, amount = amount),
        )

    /**
     * Entfernt den ausstehenden Verstärkungspool vollständig.
     */
    internal fun withoutPendingReinforcements(): GameState = copy(pendingReinforcements = null)

    /**
     * Setzt Seed und Cursor des serverseitigen Gameplay-RNGs.
     */
    internal fun withGameRandom(
        seed: Long,
        state: Long,
    ): GameState = copy(gameRandomSeed = seed, gameRandomState = state)

    /**
     * Aktualisiert nur den persistierten RNG-Cursor.
     */
    internal fun withGameRandomState(state: Long): GameState = copy(gameRandomState = state)

    /**
     * Setzt das zuggebundene Eroberungsflag für den aktiven Spieler.
     */
    internal fun withTerritoryCapturedThisTurn(captured: Boolean): GameState =
        copy(territoryCapturedThisTurn = captured)

    /**
     * Entfernt einen Spieler aus der aktiven TurnOrder, ohne ihn aus der Lobby zu entfernen.
     */
    internal fun withoutPlayerFromTurnOrder(playerId: PlayerId): GameState =
        copy(
            turnOrder = turnOrder.filterNot { it == playerId },
            activePlayer = activePlayer?.takeIf { it != playerId },
        )

    /**
     * Setzt das Flag für Pflicht-Trade-In in der nächsten Reinforcements-Phase.
     */
    internal fun withTradeRequiredOnNextReinforcementPhase(
        playerId: PlayerId,
        required: Boolean,
    ): GameState {
        require(hasPlayer(playerId)) {
            "Spieler '${playerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }

        return copy(
            tradeRequiredOnNextReinforcementPhaseByPlayer =
                tradeRequiredOnNextReinforcementPhaseByPlayer + (playerId to required),
        )
    }

    /**
     * Überträgt alle Karten eines eliminierten Spielers sofort an den Sieger.
     */
    internal fun withAllCardsTransferred(
        fromPlayerId: PlayerId,
        toPlayerId: PlayerId,
    ): GameState {
        require(hasPlayer(fromPlayerId)) {
            "Spieler '${fromPlayerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }
        require(hasPlayer(toPlayerId)) {
            "Spieler '${toPlayerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }
        require(fromPlayerId != toPlayerId) {
            "Karten können nicht auf denselben Spieler übertragen werden."
        }

        val transferredCards = handOf(fromPlayerId)
        val updatedCardsByPlayer = handState.cardsByPlayer.toMutableMap()
        updatedCardsByPlayer.remove(fromPlayerId)
        if (transferredCards.isNotEmpty()) {
            updatedCardsByPlayer[toPlayerId] = handOf(toPlayerId) + transferredCards
        }

        return copy(
            handState = handState.copy(cardsByPlayer = updatedCardsByPlayer),
        )
    }

    /**
     * Fügt der Hand eines Spielers eine Karte hinzu.
     */
    internal fun withCardAddedToHand(
        playerId: PlayerId,
        card: CardState,
    ): GameState {
        require(hasPlayer(playerId)) {
            "Spieler '${playerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }
        require(card.cardId !in deckState.cards.map(CardState::cardId)) {
            "Card '${card.cardId.value}' ist noch im Deck vorhanden."
        }
        require(card.cardId !in discardPileState.cards.map(CardState::cardId)) {
            "Card '${card.cardId.value}' ist bereits im DiscardPile vorhanden."
        }

        return copy(
            handState = handState.withCardAdded(playerId, card),
        )
    }

    /**
     * Zieht die oberste Karte aus dem Deck in die Hand eines Spielers.
     */
    internal fun withCardDrawnFromDeck(
        playerId: PlayerId,
        cardId: CardId,
    ): GameState {
        require(hasPlayer(playerId)) {
            "Spieler '${playerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }
        val drawnCard =
            deckState.topCard()
                ?: throw IllegalArgumentException("Deck ist leer.")
        require(drawnCard.cardId == cardId) {
            "Card '${cardId.value}' ist nicht die oberste Karte des Decks."
        }

        return copy(
            deckState = deckState.withoutTopCard(cardId),
            handState = handState.withCardAdded(playerId, drawnCard),
        )
    }

    /**
     * Entfernt genau eine Karte aus der Hand eines Spielers.
     */
    internal fun withoutCardFromHand(
        playerId: PlayerId,
        cardId: CardId,
    ): GameState {
        require(hasPlayer(playerId)) {
            "Spieler '${playerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }

        return copy(
            handState = handState.withoutCard(playerId, cardId),
        )
    }

    /**
     * Entfernt Karten aus einer Spielerhand und legt sie auf den Ablagestapel.
     */
    internal fun withCardsMovedFromHandToDiscard(
        playerId: PlayerId,
        cardIds: List<CardId>,
    ): GameState {
        require(hasPlayer(playerId)) {
            "Spieler '${playerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }
        require(cardIds.isNotEmpty()) {
            "cardIds darf nicht leer sein."
        }

        val cardsById = handOf(playerId).associateBy(CardState::cardId)
        val removedCards =
            cardIds.map { cardId ->
                cardsById[cardId]
                    ?: throw IllegalArgumentException(
                        "Card '${cardId.value}' ist nicht in der Hand von Spieler " +
                            "'${playerId.value}'.",
                    )
            }
        val updatedHandState =
            cardIds.fold(handState) { currentHandState, cardId ->
                currentHandState.withoutCard(playerId, cardId)
            }

        return copy(
            handState = updatedHandState,
            discardPileState = discardPileState.withCardsAdded(removedCards),
        )
    }

    /**
     * Setzt den globalen Trade-In-Zähler auf einen absoluten Wert.
     */
    internal fun withTradedInSetCount(count: Int): GameState = copy(tradedInSetCount = count)

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

    private fun allCardIds(): List<CardId> =
        handState.cardsByPlayer.values.flatten().map(CardState::cardId) +
            deckState.cards.map(CardState::cardId) +
            discardPileState.cards.map(CardState::cardId)

    private fun hasStartedMatch(): Boolean = gameStarted || status == GameStatus.RUNNING

    private fun requireMapDefinition(): MapDefinition =
        mapDefinition
            ?: throw IllegalStateException(
                "GameState enthält noch keine MapDefinition.",
            )
}
