package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.map.MapDefinitionRepository
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsSetEvent
import at.aau.pulverfass.shared.lobby.event.PlayerCardsRemovedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.PlayerKicked
import at.aau.pulverfass.shared.lobby.event.PlayerLeft
import at.aau.pulverfass.shared.lobby.event.StartPlayerConfigured
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TimeoutTriggered
import at.aau.pulverfass.shared.lobby.event.TurnEnded
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.DeckState
import at.aau.pulverfass.shared.lobby.state.DiscardPileState
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.HandState
import at.aau.pulverfass.shared.lobby.state.PendingReinforcements
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import at.aau.pulverfass.shared.message.lobby.response.MapContinentDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Rekonstruiert Lobbies aus persistierten Snapshots und Event-Folgen.
 *
 * Der Loader ist ein architekturrelevanter Bestandteil des Backends: Beim Serverstart erzeugt er
 * aus PostgreSQL-Daten wieder autoritative [GameState]-Instanzen, die anschließend in
 * [LobbyManager] registriert werden.
 *
 * @param store Event- und Snapshot-Store
 * @param mapDefinitionRepository Quelle für die Default-Map, falls nur Events vorliegen
 * @param reducer Domain-Reducer für die erneute Anwendung persistierter Events
 * @param json JSON-Konfiguration für Snapshot-Decodierung
 */
@OptIn(ExperimentalSerializationApi::class)
class LobbyRecoveryLoader(
    private val store: JdbcLobbyPersistenceStore,
    private val mapDefinitionRepository: MapDefinitionRepository,
    private val reducer: LobbyEventReducer = DefaultLobbyEventReducer(),
    private val json: Json =
        Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        },
) {
    /**
     * Rekonstruiert eine einzelne Lobby aus Snapshot und nachfolgenden Events.
     *
     * Falls noch kein Snapshot existiert, startet die Wiederherstellung von einem initialen
     * [GameState] und replayt alle verfügbaren Events.
     *
     * @return rekonstruierter Zustand oder `null`, wenn keinerlei Persistenzdaten existieren
     * @throws IllegalStateException wenn Snapshot und Event-Folge inkonsistent sind
     */
    fun restoreLobby(lobbyCode: LobbyCode): GameState? {
        val snapshotRecord = store.loadLatestSnapshot(lobbyCode)
        val baseState =
            snapshotRecord?.let { snapshot ->
                val restored =
                    json.decodeFromString(
                        PersistedLobbyRecoverySnapshot.serializer(),
                        snapshot.snapshotJson,
                    )
                restored.toGameState()
            } ?: run {
                val events = store.loadEventsAfter(lobbyCode, stateVersionExclusive = -1)
                if (events.isEmpty()) {
                    return null
                }
                GameState.initial(
                    lobbyCode = lobbyCode,
                    mapDefinition = mapDefinitionRepository.defaultMapDefinition(),
                )
            }

        val expectedStartVersion = baseState.stateVersion
        val events = store.loadEventsAfter(lobbyCode, expectedStartVersion)
        validateSequence(lobbyCode, expectedStartVersion, events)

        return events.fold(baseState) { state, persistedEvent ->
            val event = persistedEvent.toLobbyEvent()
            val updatedState = reducer.apply(state, event, context = null)
            // Die Persistenz verwendet stateVersion als lückenlosen Integritätsanker. Ein Replay,
            // das hiervon abweicht, würde auf beschädigte Daten oder eine geänderte Reducerlogik
            // hinweisen und muss deshalb den Startup abbrechen.
            check(updatedState.stateVersion == persistedEvent.stateVersion) {
                "Recovered stateVersion ${updatedState.stateVersion} stimmt nicht " +
                    "mit persistiertem Event ${persistedEvent.stateVersion} " +
                    "für Lobby '${lobbyCode.value}' überein."
            }
            updatedState
        }
    }

    /**
     * Rekonstruiert alle bekannten Lobbies und registriert sie im angegebenen [LobbyManager].
     */
    fun restoreAllInto(lobbyManager: LobbyManager) {
        restoreAll().forEach { state ->
            lobbyManager.createLobby(lobbyCode = state.lobbyCode, initialState = state)
        }
    }

    /**
     * Rekonstruiert alle Lobbies mit persistiertem Zustand.
     *
     * Die Reihenfolge ist über [LobbyCode.value] stabilisiert, damit Startup-Verhalten reproduzierbar
     * bleibt.
     */
    fun restoreAll(): List<GameState> =
        store.findLobbyCodesWithPersistedState()
            .sortedBy(LobbyCode::value)
            .mapNotNull(::restoreLobby)

    private fun validateSequence(
        lobbyCode: LobbyCode,
        snapshotStateVersion: Long,
        events: List<PersistedLobbyEventRecord>,
    ) {
        var expectedStateVersion = snapshotStateVersion + 1
        events.forEach { event ->
            check(event.stateVersion == expectedStateVersion) {
                "Persistierte Events für Lobby '${lobbyCode.value}' sind " +
                    "inkonsistent. Erwartete stateVersion $expectedStateVersion, " +
                    "gefunden ${event.stateVersion}."
            }
            expectedStateVersion += 1
        }
    }
}

/**
 * Serialisierbarer Vollsnapshot eines wiederherstellbaren Lobby-Zustands.
 *
 * Die Struktur ist absichtlich transportnah gehalten und enthält nur Daten, die für Recovery
 * oder Audit des Backends erforderlich sind.
 *
 * @property lobbyCode eindeutige Lobby-Kennung
 * @property stateVersion autoritative Versionsnummer des Zustands
 * @property processedEventCount Anzahl bereits angewandter Domain-Events
 * @property gameRandomSeed initialer Seed des deterministischen Spiels
 * @property gameRandomState letzter RNG-Zustand zum Snapshot-Zeitpunkt
 * @property lobbyOwner optionaler Owner der Lobby
 * @property players alle Spieler der Lobby in autoritativer Reihenfolge
 * @property playerDisplayNames persistierte Anzeigenamen
 * @property activePlayer aktuell aktiver Spieler, falls vorhanden
 * @property configuredStartPlayerId manuell gewählter Startspieler vor Spielstart
 * @property turnOrder persistierte Zugreihenfolge
 * @property turnNumber globaler Rundenzähler
 * @property turnState optionaler Turn-Snapshot
 * @property gameStarted Kennzeichen, ob die Partie gestartet wurde
 * @property status serialisierter [GameStatus]-Name
 * @property closedReason optionaler Schließgrund einer beendeten Lobby
 * @property lastInvalidActionReason letzte fachliche Fehlermeldung
 * @property fortifyUsedThisTurn Marker für bereits verbrauchte Fortify-Aktion
 * @property determinism determinismusrelevante Metadaten der Karte
 * @property definition serialisierte Kartenbeschreibung
 * @property territoryStates alle Territoriumszustände
 * @property setupTroopsToPlaceByPlayer verbleibende Setup-Truppen pro Spieler
 * @property pendingReinforcements noch nicht platzierte Verstärkungen
 * @property handState privater Kartenbesitz aller Spieler
 * @property deckState verbleibendes Kartendeck
 * @property discardPileState Ablagestapel des Kartendecks
 * @property tradedInSetCount Anzahl bisher eingetauschter Kartensätze
 */
@Serializable
data class PersistedLobbyRecoverySnapshot(
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val processedEventCount: Long,
    val gameRandomSeed: Long? = null,
    val gameRandomState: Long? = null,
    val lobbyOwner: PlayerId? = null,
    val players: List<PlayerId>,
    val playerDisplayNames: List<PersistedPlayerDisplayName>,
    val activePlayer: PlayerId? = null,
    val configuredStartPlayerId: PlayerId? = null,
    val turnOrder: List<PlayerId>,
    val turnNumber: Int,
    val turnState: PersistedTurnStateSnapshot? = null,
    val gameStarted: Boolean,
    val status: String,
    val closedReason: String? = null,
    val lastInvalidActionReason: String? = null,
    val fortifyUsedThisTurn: Boolean = false,
    val determinism: PublicDeterminismMetadataSnapshot,
    val definition: MapDefinitionSnapshot,
    val territoryStates: List<MapTerritoryStateSnapshot>,
    val setupTroopsToPlaceByPlayer: List<PersistedSetupTroopsSnapshot>,
    val pendingReinforcements: PendingReinforcements? = null,
    val handState: HandState = HandState(),
    val deckState: DeckState = DeckState(),
    val discardPileState: DiscardPileState = DiscardPileState(),
    val tradedInSetCount: Int = 0,
) {
    companion object {
        /**
         * Baut einen persistierbaren Snapshot aus einem laufenden [GameState].
         *
         * @throws IllegalStateException wenn der Zustand noch keine [MapDefinition] enthält
         */
        fun fromGameState(gameState: GameState): PersistedLobbyRecoverySnapshot =
            PersistedLobbyRecoverySnapshot(
                lobbyCode = gameState.lobbyCode,
                stateVersion = gameState.stateVersion,
                processedEventCount = gameState.processedEventCount,
                gameRandomSeed = gameState.gameRandomSeed,
                gameRandomState = gameState.gameRandomState,
                lobbyOwner = gameState.lobbyOwner,
                players = gameState.players,
                playerDisplayNames =
                    gameState.playerDisplayNames.entries.map { (playerId, displayName) ->
                        PersistedPlayerDisplayName(playerId = playerId, displayName = displayName)
                    },
                activePlayer = gameState.activePlayer,
                configuredStartPlayerId = gameState.configuredStartPlayerId,
                turnOrder = gameState.turnOrder,
                turnNumber = gameState.turnNumber,
                turnState = gameState.turnState?.let(PersistedTurnStateSnapshot::fromTurnState),
                gameStarted = gameState.gameStarted,
                status = gameState.status.name,
                closedReason = gameState.closedReason,
                lastInvalidActionReason = gameState.lastInvalidActionReason,
                fortifyUsedThisTurn = gameState.fortifyUsedThisTurn,
                determinism =
                    PublicDeterminismMetadataSnapshot.from(
                        gameState.mapDefinition
                            ?: error("GameState ohne MapDefinition kann nicht persistiert werden."),
                    ).copy(seed = gameState.gameRandomSeed),
                definition =
                    MapDefinitionSnapshot.from(
                        gameState.mapDefinition
                            ?: error("GameState ohne MapDefinition kann nicht persistiert werden."),
                    ),
                territoryStates =
                    gameState.allTerritoryStates().map(
                        MapTerritoryStateSnapshot::from,
                    ),
                setupTroopsToPlaceByPlayer =
                    gameState.setupTroopsToPlaceByPlayer.entries.map { (playerId, troopCount) ->
                        PersistedSetupTroopsSnapshot(
                            playerId = playerId,
                            troopCount = troopCount,
                        )
                    },
                pendingReinforcements = gameState.pendingReinforcements,
                handState = gameState.handState,
                deckState = gameState.deckState,
                discardPileState = gameState.discardPileState,
                tradedInSetCount = gameState.tradedInSetCount,
            )
    }

    /**
     * Rekonstruiert daraus wieder einen vollständigen [GameState].
     *
     * @throws IllegalStateException wenn Snapshot und Map-Metadaten nicht zusammenpassen
     */
    fun toGameState(): GameState {
        val restoredDefinition =
            definition.toMapDefinition(
                schemaVersion = determinism.schemaVersion,
            )
        // Der Hash schützt davor, versehentlich Snapshots mit einer zwischenzeitlich geänderten
        // Kartenbeschreibung zu laden. Ohne diese Prüfung könnten Recovery und laufende Regeln
        // divergieren.
        check(restoredDefinition.mapHash == determinism.mapHash) {
            "Snapshot mapHash ${determinism.mapHash} passt nicht zur " +
                "rekonstruierten MapDefinition ${restoredDefinition.mapHash}."
        }

        return GameState(
            lobbyCode = lobbyCode,
            lobbyOwner = lobbyOwner,
            players = players,
            playerDisplayNames = playerDisplayNames.associate { it.playerId to it.displayName },
            activePlayer = activePlayer,
            configuredStartPlayerId = configuredStartPlayerId,
            turnOrder = turnOrder,
            turnNumber = turnNumber,
            turnState = turnState?.toTurnState(),
            gameStarted = gameStarted,
            status = GameStatus.valueOf(status),
            stateVersion = stateVersion,
            processedEventCount = processedEventCount,
            gameRandomSeed = gameRandomSeed ?: determinism.seed,
            gameRandomState = gameRandomState ?: gameRandomSeed ?: determinism.seed,
            lastEventContext = null,
            closedReason = closedReason,
            lastInvalidActionReason = lastInvalidActionReason,
            fortifyUsedThisTurn = fortifyUsedThisTurn,
            mapDefinition = restoredDefinition,
            territoryStates =
                territoryStates.associate { snapshot ->
                    snapshot.territoryId to
                        TerritoryState(
                            territoryId = snapshot.territoryId,
                            ownerId = snapshot.ownerId,
                            troopCount = snapshot.troopCount,
                        )
                },
            setupTroopsToPlaceByPlayer =
                setupTroopsToPlaceByPlayer.associate { it.playerId to it.troopCount },
            pendingReinforcements = pendingReinforcements,
            handState = handState,
            deckState = deckState,
            discardPileState = discardPileState,
            tradedInSetCount = tradedInSetCount,
        )
    }
}

/**
 * Persistierter Anzeigename eines Spielers innerhalb eines Recovery-Snapshots.
 *
 * @property playerId referenzierter Spieler
 * @property displayName letzter autoritativer Anzeigename
 */
@Serializable
data class PersistedPlayerDisplayName(
    val playerId: PlayerId,
    val displayName: String,
)

/**
 * Persistierte Restanzahl noch zu platzierender Setup-Truppen eines Spielers.
 *
 * @property playerId referenzierter Spieler
 * @property troopCount verbleibende Anzahl zu platzierender Truppen
 */
@Serializable
data class PersistedSetupTroopsSnapshot(
    val playerId: PlayerId,
    val troopCount: Int,
)

/**
 * Serialisierbarer Snapshot des öffentlichen Turn-Zustands.
 *
 * @property activePlayerId aktuell aktiver Spieler
 * @property turnPhase aktuelle Spielphase
 * @property turnCount Rundenzähler innerhalb der Partie
 * @property startPlayerId Referenzspieler für den Rundenwechsel
 * @property isPaused signalisiert einen pausierten Turn
 * @property pauseReason optionale fachliche Begründung für die Pause
 * @property pausedPlayerId optionaler Spieler, auf dessen Rückkehr gewartet wird
 */
@Serializable
data class PersistedTurnStateSnapshot(
    val activePlayerId: PlayerId,
    val turnPhase: TurnPhase,
    val turnCount: Int,
    val startPlayerId: PlayerId,
    val isPaused: Boolean,
    val pauseReason: String? = null,
    val pausedPlayerId: PlayerId? = null,
) {
    companion object {
        /**
         * Erzeugt einen persistierbaren Snapshot aus einem laufenden [TurnState].
         */
        fun fromTurnState(turnState: TurnState): PersistedTurnStateSnapshot =
            PersistedTurnStateSnapshot(
                activePlayerId = turnState.activePlayerId,
                turnPhase = turnState.turnPhase,
                turnCount = turnState.turnCount,
                startPlayerId = turnState.startPlayerId,
                isPaused = turnState.isPaused,
                pauseReason = turnState.pauseReason,
                pausedPlayerId = turnState.pausedPlayerId,
            )
    }

    /**
     * Wandelt den Snapshot zurück in ein Domain-Modell für Recovery.
     */
    fun toTurnState(): TurnState =
        TurnState(
            activePlayerId = activePlayerId,
            turnPhase = turnPhase,
            turnCount = turnCount,
            startPlayerId = startPlayerId,
            isPaused = isPaused,
            pauseReason = pauseReason,
            pausedPlayerId = pausedPlayerId,
        )
}

private fun MapDefinitionSnapshot.toMapDefinition(schemaVersion: Int): MapDefinition =
    MapDefinition(
        schemaVersion = schemaVersion,
        territories = territories.map(MapTerritoryDefinitionSnapshot::toTerritoryDefinition),
        continents = continents.map(MapContinentDefinitionSnapshot::toContinentDefinition),
    )

private fun MapTerritoryDefinitionSnapshot.toTerritoryDefinition(): TerritoryDefinition =
    TerritoryDefinition(
        territoryId = territoryId,
        edges = edges.map(MapTerritoryEdgeSnapshot::toTerritoryEdgeDefinition),
    )

private fun MapTerritoryEdgeSnapshot.toTerritoryEdgeDefinition(): TerritoryEdgeDefinition =
    TerritoryEdgeDefinition(targetId = targetId)

private fun MapContinentDefinitionSnapshot.toContinentDefinition(): ContinentDefinition =
    ContinentDefinition(
        continentId = continentId,
        territoryIds = territoryIds,
        bonusValue = bonusValue,
    )

internal fun PersistedLobbyEventRecord.toLobbyEvent(): LobbyEvent {
    val jsonObject = Json.parseToJsonElement(eventJson).jsonObject
    val jsonLobbyCode = LobbyCode(jsonObject.string("lobbyCode"))
    check(jsonLobbyCode == lobbyCode) {
        "Persistiertes Event referenziert Lobby '${jsonLobbyCode.value}', " +
            "erwartet war '${lobbyCode.value}'."
    }

    return when (eventType) {
        "lobby_created" -> LobbyCreated(lobbyCode)
        "attack_resolved" ->
            AttackResolvedEvent(
                lobbyCode = lobbyCode,
                attackerPlayerId = PlayerId(jsonObject.long("attackerPlayerId")),
                defenderPlayerId = PlayerId(jsonObject.long("defenderPlayerId")),
                fromTerritoryId = TerritoryId(jsonObject.string("fromTerritoryId")),
                toTerritoryId = TerritoryId(jsonObject.string("toTerritoryId")),
                attackTroops = jsonObject.int("attackTroops"),
                sourceTroopsBefore = jsonObject.int("sourceTroopsBefore"),
                targetTroopsBefore = jsonObject.int("targetTroopsBefore"),
                requestedAttackDice = jsonObject.int("requestedAttackDice"),
                attackDice = jsonObject.int("attackDice"),
                defendDice = jsonObject.int("defendDice"),
                attackerRolls = jsonObject.intList("attackerRolls"),
                defenderRolls = jsonObject.intList("defenderRolls"),
                rngTrace = jsonObject.intList("rngTrace"),
                rngStateBefore = jsonObject.long("rngStateBefore"),
                rngStateAfter = jsonObject.long("rngStateAfter"),
                attackerLosses = jsonObject.int("attackerLosses"),
                defenderLosses = jsonObject.int("defenderLosses"),
                attackerRemaining = jsonObject.int("attackerRemaining"),
                defenderRemaining = jsonObject.int("defenderRemaining"),
                occupyingTroopCount = jsonObject.nullableInt("occupyingTroopCount"),
                minOccupyingTroops = jsonObject.nullableInt("minOccupyingTroops"),
            )
        "player_eliminated" ->
            PlayerEliminatedEvent(
                lobbyCode = lobbyCode,
                playerId = PlayerId(jsonObject.long("playerId")),
                eliminatedByPlayerId = PlayerId(jsonObject.long("eliminatedByPlayerId")),
                stateVersion = jsonObject.nullableLong("stateVersion"),
            )
        "card_set_traded_in" ->
            CardSetTradedInEvent(
                lobbyCode = lobbyCode,
                playerId = PlayerId(jsonObject.long("playerId")),
                cardIds = jsonObject.stringList("cardIds").map(::CardId),
                value = jsonObject.int("value"),
                tradeIndex = jsonObject.int("tradeIndex"),
            )
        "pending_reinforcements_set" ->
            PendingReinforcementsSetEvent(
                lobbyCode = lobbyCode,
                playerId = PlayerId(jsonObject.long("playerId")),
                amount = jsonObject.int("amount"),
            )
        "pending_reinforcements_changed" ->
            PendingReinforcementsChangedEvent(
                lobbyCode = lobbyCode,
                playerId = PlayerId(jsonObject.long("playerId")),
                delta = jsonObject.int("delta"),
            )
        "player_cards_removed" ->
            PlayerCardsRemovedEvent(
                lobbyCode = lobbyCode,
                playerId = PlayerId(jsonObject.long("playerId")),
                cardIds = jsonObject.stringList("cardIds").map(::CardId),
            )
        "lobby_closed" -> LobbyClosed(lobbyCode, reason = jsonObject.nullableString("reason"))
        "player_joined" ->
            PlayerJoined(
                lobbyCode = lobbyCode,
                playerId = PlayerId(jsonObject.long("playerId")),
                playerDisplayName = jsonObject.string("playerDisplayName"),
            )
        "player_left" ->
            PlayerLeft(
                lobbyCode = lobbyCode,
                playerId = PlayerId(jsonObject.long("playerId")),
                reason = jsonObject.nullableString("reason"),
            )
        "player_kicked" ->
            PlayerKicked(
                lobbyCode = lobbyCode,
                targetPlayerId = PlayerId(jsonObject.long("targetPlayerId")),
                requesterPlayerId = PlayerId(jsonObject.long("requesterPlayerId")),
            )
        "start_player_configured" ->
            StartPlayerConfigured(
                lobbyCode = lobbyCode,
                startPlayerId = PlayerId(jsonObject.long("startPlayerId")),
                requesterPlayerId = PlayerId(jsonObject.long("requesterPlayerId")),
            )
        "game_started" ->
            GameStarted(
                lobbyCode = lobbyCode,
                randomSeed = jsonObject.long("randomSeed"),
            )
        "invalid_action_detected" ->
            InvalidActionDetected(
                lobbyCode = lobbyCode,
                playerId = jsonObject.nullableLong("playerId")?.let(::PlayerId),
                reason = jsonObject.string("reason"),
            )
        "system_tick" ->
            SystemTick(
                lobbyCode = lobbyCode,
                tick = jsonObject.long("tick"),
            )
        "territory_owner_changed" ->
            TerritoryOwnerChangedEvent(
                lobbyCode = lobbyCode,
                territoryId = TerritoryId(jsonObject.string("territoryId")),
                ownerId = jsonObject.nullableLong("ownerId")?.let(::PlayerId),
                stateVersion = jsonObject.nullableLong("stateVersion"),
            )
        "territory_troops_changed" ->
            TerritoryTroopsChangedEvent(
                lobbyCode = lobbyCode,
                territoryId = TerritoryId(jsonObject.string("territoryId")),
                troopCount = jsonObject.int("troopCount"),
                stateVersion = jsonObject.nullableLong("stateVersion"),
            )
        "fortify_move_applied" ->
            FortifyMoveAppliedEvent(
                lobbyCode = lobbyCode,
                playerId = PlayerId(jsonObject.long("playerId")),
                fromTerritoryId = TerritoryId(jsonObject.string("fromTerritoryId")),
                toTerritoryId = TerritoryId(jsonObject.string("toTerritoryId")),
                troopCount = jsonObject.int("troopCount"),
            )
        "fortify_used_set" ->
            FortifyUsedSetEvent(
                lobbyCode = lobbyCode,
                used = jsonObject.boolean("used"),
            )
        "timeout_triggered" ->
            TimeoutTriggered(
                lobbyCode = lobbyCode,
                target = jsonObject.string("target"),
                timeoutMillis = jsonObject.long("timeoutMillis"),
            )
        "turn_ended" ->
            TurnEnded(
                lobbyCode = lobbyCode,
                playerId = PlayerId(jsonObject.long("playerId")),
            )
        "turn_state_updated" ->
            TurnStateUpdatedEvent(
                lobbyCode = lobbyCode,
                activePlayerId = PlayerId(jsonObject.long("activePlayerId")),
                turnPhase = TurnPhase.valueOf(jsonObject.string("turnPhase")),
                turnCount = jsonObject.int("turnCount"),
                startPlayerId = PlayerId(jsonObject.long("startPlayerId")),
                isPaused = jsonObject.boolean("isPaused"),
                pauseReason = jsonObject.nullableString("pauseReason"),
                pausedPlayerId = jsonObject.nullableLong("pausedPlayerId")?.let(::PlayerId),
            )
        else -> error("Unbekannter persistierter eventType '$eventType'.")
    }
}

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.stringList(key: String): List<String> =
    getValue(key).jsonArray.map { element -> element.jsonPrimitive.content }

private fun JsonObject.intList(key: String): List<Int> =
    getValue(key).jsonArray.map { element ->
        element.jsonPrimitive.intOrNull ?: error("Feld '$key' muss eine Int-Liste sein.")
    }

private fun JsonObject.nullableString(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.long(key: String): Long =
    getValue(key).jsonPrimitive.longOrNull
        ?: error("Feld '$key' muss ein Long sein.")

private fun JsonObject.nullableLong(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

private fun JsonObject.int(key: String): Int =
    getValue(key).jsonPrimitive.intOrNull
        ?: error("Feld '$key' muss ein Int sein.")

private fun JsonObject.nullableInt(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.boolean(key: String): Boolean =
    getValue(key).jsonPrimitive.booleanOrNull
        ?: error("Feld '$key' muss ein Boolean sein.")
