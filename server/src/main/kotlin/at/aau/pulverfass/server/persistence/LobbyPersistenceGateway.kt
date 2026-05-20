package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.server.DatabaseReadiness
import at.aau.pulverfass.server.DatabaseReadinessProbe
import at.aau.pulverfass.server.DatabaseReadinessState
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsSetEvent
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

interface LobbyPersistenceCallbacks : DatabaseReadinessProbe {
    suspend fun onLobbyEventAccepted(
        event: LobbyEvent,
        previousState: GameState,
        currentState: GameState,
    )

    suspend fun onPhaseBoundaryBroadcast(payload: PhaseBoundaryEvent)

    suspend fun onSnapshotBroadcast(payload: GameStateSnapshotBroadcast)

    suspend fun onSnapshotBroadcast(
        currentState: GameState,
        payload: GameStateSnapshotBroadcast,
    )

    companion object {
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

    override fun readiness(): DatabaseReadiness =
        lastFailureDetail?.let { detail ->
            DatabaseReadiness(
                state = DatabaseReadinessState.DOWN,
                detail = detail,
            )
        } ?: DatabaseReadiness(DatabaseReadinessState.UP)

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

    override suspend fun onSnapshotBroadcast(payload: GameStateSnapshotBroadcast) {
        error("Use onSnapshotBroadcast(currentState, payload).")
    }

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

private fun LobbyEvent.toPersistedPayload(): PersistedEventPayload =
    when (this) {
        is LobbyCreated ->
            persistedPayload(
                type = "lobby_created",
                "lobbyCode" to lobbyCode.value,
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
        is LobbyClosed ->
            persistedPayload(
                type = "lobby_closed",
                "lobbyCode" to lobbyCode.value,
                "reason" to reason,
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
