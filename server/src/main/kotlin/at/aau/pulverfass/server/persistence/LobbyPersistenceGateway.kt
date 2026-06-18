package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.server.DatabaseReadiness
import at.aau.pulverfass.server.DatabaseReadinessProbe
import at.aau.pulverfass.server.DatabaseReadinessState
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardDrawnEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.CheatReinforcementBonusUsedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.MatchEndedEvent
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
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Callback-Schnittstelle zwischen Lobby-Laufzeit und Persistenzschicht.
 *
 * Implementierungen reagieren auf akzeptierte Domain-Events und auf Snapshot-Broadcasts, damit
 * Recovery-Daten unabhängig von der eigentlichen Spielorchestrierung gespeichert werden können.
 */
interface LobbyPersistenceCallbacks : DatabaseReadinessProbe {
    /**
     * Wird nach erfolgreicher Anwendung eines Domain-Events aufgerufen.
     *
     * @param event akzeptiertes Domain-Event
     * @param previousState Zustand direkt vor der Event-Anwendung
     * @param currentState Zustand direkt nach der Event-Anwendung
     */
    suspend fun onLobbyEventAccepted(
        event: LobbyEvent,
        previousState: GameState,
        currentState: GameState,
    )

    /**
     * Beobachtet einen Broadcast an einer Phasengrenze.
     *
     * Die Standardimplementierung persistiert diesen Broadcast nicht separat, weil die Information
     * aus Event-Store und Snapshot wieder ableitbar ist.
     */
    suspend fun onPhaseBoundaryBroadcast(payload: PhaseBoundaryEvent)

    /**
     * Legacy-Variante für Snapshot-Broadcasts ohne direkten GameState.
     *
     * Neue Aufrufer sollen die Überladung mit [GameState] verwenden.
     */
    suspend fun onSnapshotBroadcast(payload: GameStateSnapshotBroadcast)

    /**
     * Persistiert einen transportierbaren Snapshot zusammen mit dem dazugehörigen autoritativen
     * Zustand.
     */
    suspend fun onSnapshotBroadcast(
        currentState: GameState,
        payload: GameStateSnapshotBroadcast,
    )

    companion object {
        /**
         * Liefert eine No-op-Implementierung für Deployments ohne Persistenz.
         */
        fun disabled(): LobbyPersistenceCallbacks = DisabledLobbyPersistenceCallbacks
    }
}

private object DisabledLobbyPersistenceCallbacks : LobbyPersistenceCallbacks {
    override fun readiness(): DatabaseReadiness =
        DatabaseReadiness(
            state = DatabaseReadinessState.DISABLED,
            detail = "Lobby persistence is disabled.",
        )

    override suspend fun onLobbyEventAccepted(
        event: LobbyEvent,
        previousState: GameState,
        currentState: GameState,
    ) = Unit

    override suspend fun onPhaseBoundaryBroadcast(payload: PhaseBoundaryEvent) = Unit

    override suspend fun onSnapshotBroadcast(payload: GameStateSnapshotBroadcast) = Unit

    override suspend fun onSnapshotBroadcast(
        currentState: GameState,
        payload: GameStateSnapshotBroadcast,
    ) = Unit
}

/**
 * Produktive Persistenzanbindung für Lobby-Events und Recovery-Snapshots.
 *
 * Fehler beim Schreiben werden absichtlich nicht bis in die Spiellogik propagiert. Stattdessen
 * bleibt der laufende Server verfügbar und meldet den Persistenzfehler über den Health-Check.
 *
 * @param store JDBC-Store für Events und Snapshots
 * @param closeAction optionaler Cleanup-Hook für abhängige Ressourcen
 * @param json JSON-Konfiguration für Snapshot-Persistierung
 */
@OptIn(ExperimentalSerializationApi::class)
class DatabaseBackedLobbyPersistenceGateway(
    private val store: JdbcLobbyPersistenceStore,
    private val closeAction: () -> Unit = {},
    private val json: Json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        },
) : LobbyPersistenceCallbacks {
    private val logger = LoggerFactory.getLogger(DatabaseBackedLobbyPersistenceGateway::class.java)

    @Volatile
    private var lastFailureDetail: String? = null

    /**
     * Liefert den zuletzt beobachteten Persistenzzustand.
     *
     * Sobald eine Schreiboperation fehlschlägt, meldet der Probe `DOWN`, bis die nächste
     * Persistierung erfolgreich war.
     */
    override fun readiness(): DatabaseReadiness =
        lastFailureDetail?.let { detail ->
            DatabaseReadiness(
                state = DatabaseReadinessState.DOWN,
                detail = detail,
            )
        } ?: DatabaseReadiness(DatabaseReadinessState.UP)

    /**
     * Persistiert ein akzeptiertes Domain-Event im Event-Store.
     */
    override suspend fun onLobbyEventAccepted(
        event: LobbyEvent,
        previousState: GameState,
        currentState: GameState,
    ) {
        persist("Persisting lobby event '${event::class.simpleName}' failed") {
            val persistedEvent = event.toPersistedPayload()
            store.appendEvent(
                lobbyCode = currentState.lobbyCode,
                stateVersion = currentState.stateVersion,
                turnCount = currentState.resolvedTurnState?.turnCount ?: currentState.turnNumber,
                eventType = persistedEvent.type,
                eventJson = persistedEvent.payload,
            )
        }
    }

    override suspend fun onPhaseBoundaryBroadcast(payload: PhaseBoundaryEvent) {
        // Phase boundaries are derived transport metadata and currently share the same
        // stateVersion as the accepted domain event. Because the event store enforces
        // one row per (lobby_code, state_version), we do not persist them separately.
    }

    /**
     * Diese Legacy-Signatur bleibt absichtlich unerfüllt, weil für Recovery immer auch der
     * dazugehörige [GameState] benötigt wird.
     */
    override suspend fun onSnapshotBroadcast(payload: GameStateSnapshotBroadcast) {
        error("Use onSnapshotBroadcast(currentState, payload).")
    }

    /**
     * Persistiert einen vollständigen Recovery-Snapshot des aktuellen Spielzustands.
     */
    override suspend fun onSnapshotBroadcast(
        currentState: GameState,
        payload: GameStateSnapshotBroadcast,
    ) {
        persist("Persisting full snapshot failed") {
            store.appendSnapshot(
                lobbyCode = payload.lobbyCode,
                stateVersion = payload.stateVersion,
                turnCount = payload.turnState.turnCount,
                snapshotJson =
                    json.encodeToJsonElement(
                        PersistedLobbyRecoverySnapshot.serializer(),
                        PersistedLobbyRecoverySnapshot.fromGameState(currentState),
                    ),
            )
        }
    }

    override fun close() {
        closeAction()
    }

    /**
     * Führt einen Persistenzschritt aus und merkt sich den letzten Fehlerzustand für Health-Checks.
     */
    private inline fun persist(
        failurePrefix: String,
        block: () -> Unit,
    ) {
        try {
            block()
            lastFailureDetail = null
        } catch (exception: Exception) {
            lastFailureDetail =
                "$failurePrefix: ${exception.message ?: exception::class.simpleName ?: "unknown"}"
            logger.error(lastFailureDetail, exception)
        }
    }
}

private data class PersistedEventPayload(
    val type: String,
    val payload: JsonElement,
)

private val stringListSerializer = ListSerializer(String.serializer())

private fun LobbyEvent.toPersistedPayload(): PersistedEventPayload =
    when (this) {
        is LobbyCreated ->
            persistedPayload(
                type = "lobby_created",
                "lobbyCode" to lobbyCode.value,
            )
        is AttackResolvedEvent ->
            PersistedEventPayload(
                type = "attack_resolved",
                payload =
                    buildJsonObject {
                        put("lobbyCode", lobbyCode.value)
                        put("attackerPlayerId", attackerPlayerId.value)
                        put("defenderPlayerId", defenderPlayerId.value)
                        put("fromTerritoryId", fromTerritoryId.value)
                        put("toTerritoryId", toTerritoryId.value)
                        put("attackTroops", attackTroops)
                        put("sourceTroopsBefore", sourceTroopsBefore)
                        put("targetTroopsBefore", targetTroopsBefore)
                        put("requestedAttackDice", requestedAttackDice)
                        put("attackDice", attackDice)
                        put("defendDice", defendDice)
                        put(
                            "attackerRolls",
                            Json.Default.encodeToJsonElement(
                                ListSerializer(Int.serializer()),
                                attackerRolls,
                            ),
                        )
                        put(
                            "defenderRolls",
                            Json.Default.encodeToJsonElement(
                                ListSerializer(Int.serializer()),
                                defenderRolls,
                            ),
                        )
                        put(
                            "rngTrace",
                            Json.Default.encodeToJsonElement(
                                ListSerializer(Int.serializer()),
                                rngTrace,
                            ),
                        )
                        put("rngStateBefore", rngStateBefore)
                        put("rngStateAfter", rngStateAfter)
                        put("attackerLosses", attackerLosses)
                        put("defenderLosses", defenderLosses)
                        put("attackerRemaining", attackerRemaining)
                        put("defenderRemaining", defenderRemaining)
                        put("occupyingTroopCount", occupyingTroopCount)
                        put("minOccupyingTroops", minOccupyingTroops)
                    },
            )
        is PlayerEliminatedEvent ->
            persistedPayload(
                type = "player_eliminated",
                "lobbyCode" to lobbyCode.value,
                "playerId" to playerId.value,
                "eliminatedByPlayerId" to eliminatedByPlayerId.value,
                "stateVersion" to stateVersion,
            )
        is CardSetTradedInEvent ->
            PersistedEventPayload(
                type = "card_set_traded_in",
                payload =
                    buildJsonObject {
                        put("lobbyCode", lobbyCode.value)
                        put("playerId", playerId.value)
                        put("value", value)
                        put("tradeIndex", tradeIndex)
                        put(
                            "cardIds",
                            Json.Default.encodeToJsonElement(
                                stringListSerializer,
                                cardIds.map(CardId::value),
                            ),
                        )
                    },
            )
        is CardDrawnEvent ->
            persistedPayload(
                type = "card_drawn",
                "lobbyCode" to lobbyCode.value,
                "playerId" to playerId.value,
                "cardId" to cardId.value,
            )
        is PendingReinforcementsSetEvent ->
            persistedPayload(
                type = "pending_reinforcements_set",
                "lobbyCode" to lobbyCode.value,
                "playerId" to playerId.value,
                "amount" to amount,
            )
        is PendingReinforcementsChangedEvent ->
            persistedPayload(
                type = "pending_reinforcements_changed",
                "lobbyCode" to lobbyCode.value,
                "playerId" to playerId.value,
                "delta" to delta,
            )
        /*
         * Der verbrauchte Cheatbonus muss im Eventlog landen. Sonst könnte ein
         * Spieler nach einem Server-Neustart denselben einmaligen Bonus erneut
         * benutzen, obwohl der GameState ihn vor dem Neustart schon verbraucht hatte.
         */
        is CheatReinforcementBonusUsedEvent ->
            persistedPayload(
                type = "cheat_reinforcement_bonus_used",
                "lobbyCode" to lobbyCode.value,
                "playerId" to playerId.value,
            )
        is PlayerCardsRemovedEvent ->
            PersistedEventPayload(
                type = "player_cards_removed",
                payload =
                    buildJsonObject {
                        put("lobbyCode", lobbyCode.value)
                        put("playerId", playerId.value)
                        put(
                            "cardIds",
                            Json.Default.encodeToJsonElement(
                                stringListSerializer,
                                cardIds.map(CardId::value),
                            ),
                        )
                    },
            )
        is LobbyClosed ->
            persistedPayload(
                type = "lobby_closed",
                "lobbyCode" to lobbyCode.value,
                "reason" to reason,
            )
        is MatchEndedEvent ->
            persistedPayload(
                type = "match_ended",
                "lobbyCode" to lobbyCode.value,
                "reason" to reason.name,
            )
        is PlayerJoined ->
            persistedPayload(
                type = "player_joined",
                "lobbyCode" to lobbyCode.value,
                "playerId" to playerId.value,
                "playerDisplayName" to playerDisplayName,
            )
        is PlayerLeft ->
            persistedPayload(
                type = "player_left",
                "lobbyCode" to lobbyCode.value,
                "playerId" to playerId.value,
                "reason" to reason,
            )
        is PlayerKicked ->
            persistedPayload(
                type = "player_kicked",
                "lobbyCode" to lobbyCode.value,
                "targetPlayerId" to targetPlayerId.value,
                "requesterPlayerId" to requesterPlayerId.value,
            )
        is StartPlayerConfigured ->
            persistedPayload(
                type = "start_player_configured",
                "lobbyCode" to lobbyCode.value,
                "startPlayerId" to startPlayerId.value,
                "requesterPlayerId" to requesterPlayerId.value,
            )
        is GameStarted ->
            persistedPayload(
                type = "game_started",
                "lobbyCode" to lobbyCode.value,
                "randomSeed" to randomSeed,
            )
        is InvalidActionDetected ->
            persistedPayload(
                type = "invalid_action_detected",
                "lobbyCode" to lobbyCode.value,
                "playerId" to playerId?.value,
                "reason" to reason,
            )
        is SystemTick ->
            persistedPayload(
                type = "system_tick",
                "lobbyCode" to lobbyCode.value,
                "tick" to tick,
            )
        is TerritoryOwnerChangedEvent ->
            persistedPayload(
                type = "territory_owner_changed",
                "lobbyCode" to lobbyCode.value,
                "territoryId" to territoryId.value,
                "ownerId" to ownerId?.value,
                "stateVersion" to stateVersion,
            )
        is TerritoryTroopsChangedEvent ->
            persistedPayload(
                type = "territory_troops_changed",
                "lobbyCode" to lobbyCode.value,
                "territoryId" to territoryId.value,
                "troopCount" to troopCount,
                "stateVersion" to stateVersion,
            )
        is FortifyMoveAppliedEvent ->
            persistedPayload(
                type = "fortify_move_applied",
                "lobbyCode" to lobbyCode.value,
                "playerId" to playerId.value,
                "fromTerritoryId" to fromTerritoryId.value,
                "toTerritoryId" to toTerritoryId.value,
                "troopCount" to troopCount,
            )
        is FortifyUsedSetEvent ->
            persistedPayload(
                type = "fortify_used_set",
                "lobbyCode" to lobbyCode.value,
                "used" to used,
            )
        is TimeoutTriggered ->
            persistedPayload(
                type = "timeout_triggered",
                "lobbyCode" to lobbyCode.value,
                "target" to target,
                "timeoutMillis" to timeoutMillis,
            )
        is TurnEnded ->
            persistedPayload(
                type = "turn_ended",
                "lobbyCode" to lobbyCode.value,
                "playerId" to playerId.value,
            )
        is TurnStateUpdatedEvent ->
            persistedPayload(
                type = "turn_state_updated",
                "lobbyCode" to lobbyCode.value,
                "activePlayerId" to activePlayerId.value,
                "turnPhase" to turnPhase.name,
                "turnCount" to turnCount,
                "startPlayerId" to startPlayerId.value,
                "isPaused" to isPaused,
                "pauseReason" to pauseReason,
                "pausedPlayerId" to pausedPlayerId?.value,
            )
    }

private fun persistedPayload(
    type: String,
    vararg fields: Pair<String, Any?>,
): PersistedEventPayload =
    PersistedEventPayload(
        type = type,
        payload =
            buildJsonObject {
                fields.forEach { (key, value) ->
                    putJsonValue(key, value)
                }
            },
    )

private fun kotlinx.serialization.json.JsonObjectBuilder.putJsonValue(
    key: String,
    value: Any?,
) {
    when (value) {
        null -> put(key, JsonNull)
        is Boolean -> put(key, value)
        is Int -> put(key, value)
        is Long -> put(key, value)
        is String -> put(key, value)
        is PlayerId -> put(key, value.value)
        is TerritoryId -> put(key, value.value)
        else -> put(key, value.toString())
    }
}
