package at.aau.pulverfass.app.lobby

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.game.AutoAttackIntent
import at.aau.pulverfass.app.game.AutoAttackUiState
import at.aau.pulverfass.app.game.ClientGameStateReducer
import at.aau.pulverfass.app.game.GameMapTerritoryMapper
import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.app.game.MIN_ATTACK_TROOPS
import at.aau.pulverfass.app.game.minimumOccupyingTroopsForAttack
import at.aau.pulverfass.app.network.ClientNetwork
import at.aau.pulverfass.app.storage.NoOpPlayerNameStore
import at.aau.pulverfass.app.storage.NoOpReconnectSessionStore
import at.aau.pulverfass.app.storage.PlayerNameStore
import at.aau.pulverfass.app.storage.ReconnectSessionStore
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.normalizePlayerDisplayName
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import at.aau.pulverfass.shared.message.connection.event.GlobalPlayerCountEvent
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.CharacterSelectedBroadcast
import at.aau.pulverfass.shared.message.lobby.event.ConnectionStatusUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.MatchEndedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerCountUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.AttackRequest
import at.aau.pulverfass.shared.message.lobby.request.CharacterSelectRequest
import at.aau.pulverfass.shared.message.lobby.request.ClaimCheatReinforcementBonusRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmAttackDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmReinforcementsDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.FortifyMoveRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpReason
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequest
import at.aau.pulverfass.shared.message.lobby.request.ReportCheatRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.TerritoryPlacement
import at.aau.pulverfass.shared.message.lobby.request.TradeInCardsRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.AttackResponse
import at.aau.pulverfass.shared.message.lobby.response.CharacterSelectResponse
import at.aau.pulverfass.shared.message.lobby.response.ClaimCheatReinforcementBonusResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmAttackDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmReinforcementsDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.FortifyMoveResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.PlaceReinforcementsResponse
import at.aau.pulverfass.shared.message.lobby.response.ReportCheatResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.TradeInCardsResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CharacterSelectErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ClaimCheatReinforcementBonusErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ReportCheatErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import at.aau.pulverfass.shared.network.transport.Connected
import at.aau.pulverfass.shared.network.transport.Disconnected
import at.aau.pulverfass.shared.network.transport.TransportError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI-zentrierte Lobby-Schicht für die Android-App.
 *
 * Der Controller verbindet den LobbyScreen mit der technischen
 * WebSocket-Pipeline und kapselt Statusverwaltung, Fehlerbehandlung
 * sowie Create/Join-Flow inklusive Lobby-Playerliste.
 *
 * @param scope CoroutineScope für Transport, Decoder und ausgehende Requests
 * @param network technische WebSocket- und Packet-Schicht
 * @param config zentrale Texte und Retry-Grenzen für den Lobby-Flow
 * @param reconnectSessionStore kleine lokale Persistenz für Session-Token und
 * Reconnect-Metadaten nach App-Neustart
 * @param playerNameStore lokale Persistenz für den zuletzt gewählten Anzeigenamen
 */
class LobbyController(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val network: ClientNetwork = ClientNetwork(scope),
    private val config: LobbyControllerConfig = LobbyControllerConfig(),
    private val reconnectSessionStore: ReconnectSessionStore = NoOpReconnectSessionStore,
    private val playerNameStore: PlayerNameStore = NoOpPlayerNameStore,
) {
    private enum class PendingLobbyAction {
        CREATE,
        JOIN,
    }

    private data class AutoAttackRequestPlan(
        val lobbyCode: LobbyCode,
        val playerId: PlayerId,
        val fromTerritoryId: TerritoryId,
        val toTerritoryId: TerritoryId,
        val attackTroops: Int,
        val moveAfterCapture: Int,
    )

    private val wasGameStartedOnLastAppRun =
        reconnectSessionStore.readWasGameStarted()

    private val _state =
        MutableStateFlow(
            run {
                val savedCharacterId = playerNameStore.readCharacterId()
                val savedAutoAttackEnabled = playerNameStore.readAutoAttackEnabled()
                val savedPlayerName =
                    normalizePlayerDisplayName(playerNameStore.readPlayerName().orEmpty())
                playerNameStore.savePlayerName(savedPlayerName)
                LobbyUiState(
                    serverUrl = reconnectSessionStore.readServerUrl() ?: config.defaultServerUrl,
                    playerName = savedPlayerName,
                    statusText = config.statusNotConnected,
                    sessionToken = reconnectSessionStore.readSessionToken(),
                    gameStarted = wasGameStartedOnLastAppRun,
                    gameState =
                        GameUiState(isStarted = wasGameStartedOnLastAppRun)
                            .withAutoAttackPreference(savedAutoAttackEnabled),
                    characterId = savedCharacterId,
                    autoAttackEnabled = savedAutoAttackEnabled,
                )
            },
        )
    val state: StateFlow<LobbyUiState> = _state.asStateFlow()

    /*
     * Die Lobby-Playerliste kommt als einzelne Join/Leave/Kick-Events. Eine Map
     * nach PlayerId verhindert Duplikate und erlaubt späteres Publizieren als
     * geordnete UI-Liste.
     */
    private val playersById = linkedMapOf<Long, LobbyPlayerUi>()
    private val commandDispatcher = LobbyCommandDispatcher(network::sendPayload)
    private var clearCheatErrorJob: Job? = null
    private var pendingCreateCallback: ((String) -> Unit)? = null
    private var pendingJoinCallback: ((String) -> Unit)? = null
    private var pendingLobbyAction: PendingLobbyAction? = null

    /*
     * Reconnect-Zustand getrennt vom UI-State: Die UI darf sehen, dass reconnectet
     * wird, aber nicht den provisorischen neuen Token überschreiben. Der alte
     * Token wird hier gehalten, bis der Server die Session bestätigt.
     */
    private var reconnectJob: Job? = null
    private var reconnectSessionToken: SessionToken? = null
    private var awaitingReconnectResponse = false
    private var manualDisconnectRequested = false
    private var suppressNextAttackBoundaryNotice = false
    private var delayedAutoPhaseAdvanceJob: Job? = null
    private var delayedAutoPhaseAdvanceDeadlineMillis: Long? = null
    private var deferredOwnAttackPhaseState: GameUiState? = null
    private var deferredOwnAttackPhaseBoundary: PhaseBoundaryEvent? = null
    private var manuallyConsumedAttackBoundaryStateVersion: Long? = null
    private var autoAttackRequestSequence: Long = 0L
    private var delayedAutoAttackContinuationJob: Job? = null

    init {
        scope.launch {
            /*
             * Transportevents beschreiben nur Socket-Lifecycle. Fachliche
             * Entscheidungen wie "Reconnect starten" oder "Pending Create senden"
             * liegen hier im Controller.
             */
            network.transport.events.collect { event ->
                when (event) {
                    is Connected -> handleConnected()

                    is Disconnected ->
                        handleConnectionLost(
                            statusText = config.statusDisconnected,
                            errorText = null,
                            gameErrorText = config.errorDisconnectedDuringGame,
                        )

                    is TransportError -> {
                        val message = event.cause.message ?: config.errorTransportUnknown
                        handleConnectionLost(
                            statusText = config.statusConnectionError,
                            errorText = message,
                            gameErrorText = message,
                        )
                    }

                    else -> Unit
                }
            }
        }

        scope.launch {
            /*
             * Alle fachlichen Pakete laufen durch denselben Decoder. Danach
             * entscheidet handlePayload, ob das Paket Lobby, Reconnect oder Game
             * betrifft.
             */
            network.packetReceiver.packets.collect { packet ->
                _state.update { it.copy(lastMessageType = packet.header.type.name) }

                runCatching {
                    MessageCodec.decodePayload(packet)
                }.onSuccess { payload ->
                    handlePayload(payload)
                }.onFailure { error ->
                    handlePacketDecodeFailure(error)
                }
            }
        }

        scope.launch {
            network.packetReceiver.errors.collect { error ->
                handlePacketDecodeFailure(error)
            }
        }

        /*
         * Wenn die App nach einem Prozessende neu startet, gibt es keinen
         * In-Memory-Zustand mehr. Ein gespeicherter Token ist deshalb das
         * Signal, direkt eine neue technische Verbindung aufzubauen und danach
         * den alten Token per ReconnectRequest vorzulegen.
         */
        if (state.value.sessionToken != null) {
            beginReconnect(state.value)
        }
    }

    fun updateServerUrl(serverUrl: String) {
        reconnectSessionStore.saveServerUrl(serverUrl)
        _state.update { it.copy(serverUrl = serverUrl) }
    }

    fun updatePlayerName(playerName: String) {
        val normalizedPlayerName = normalizePlayerDisplayName(playerName)
        playerNameStore.savePlayerName(normalizedPlayerName)
        _state.update { it.copy(playerName = normalizedPlayerName) }
    }

    fun updatePlayerColor(color: Color) {
        _state.update { it.copy(playerColor = color) }
    }

    fun updateCharacter(id: String) {
        playerNameStore.saveCharacterId(id)
        _state.update { it.copy(characterId = id) }
    }

    fun selectCharacter(characterId: String) {
        val lobbyCode = state.value.activeLobbyCode ?: return
        val playerId = state.value.ownPlayerId ?: return
        scope.launch {
            runCatching {
                commandDispatcher.send(
                    LobbyCommand(
                        key = LobbyCommandKey.CHARACTER_SELECT,
                        payload =
                            CharacterSelectRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                playerId = playerId,
                                characterId = characterId,
                            ),
                    ),
                )
            }
        }
    }

    fun clearCharacterSelectError() {
        _state.update { it.copy(characterSelectError = null) }
    }

    /**
     * Schließt die aktuelle Auto-Phasenmeldung und zeigt ggf. die nächste Queue-Meldung.
     *
     * @see enqueueAutoPhaseNotice
     */
    fun clearAutoPhaseNotice() {
        var shouldCheckNextPhase = false
        _state.update { current ->
            val nextNotice = current.autoPhaseNoticeQueue.firstOrNull()
            if (nextNotice == null) {
                shouldCheckNextPhase = true
                current.copy(
                    autoPhaseNoticeText = null,
                    autoPhaseNoticeQueue = emptyList(),
                )
            } else {
                current.copy(
                    autoPhaseNoticeText = nextNotice,
                    autoPhaseNoticeQueue = current.autoPhaseNoticeQueue.drop(1),
                )
            }
        }
        if (shouldCheckNextPhase) {
            maybeAdvanceCurrentPhaseAutomatically()
        }
    }

    fun clearCheatReportNotice() {
        _state.update { it.copy(cheatReportNoticeText = null) }
    }

    /**
     * Fügt eine Auto-Phasenmeldung dedupliziert in die UI-Queue ein.
     *
     * Mehrere Phasen können direkt hintereinander automatisch übersprungen
     * werden. Die Queue sorgt dafür, dass jede fachlich relevante Meldung
     * einzeln sichtbar wird, statt die vorherige Anzeige zu überschreiben.
     *
     * @param message sichtbarer Text der Auto-Phasenmeldung.
     */
    private fun enqueueAutoPhaseNotice(message: String) {
        _state.update { current -> current.withQueuedAutoPhaseNotice(message) }
    }

    private fun LobbyUiState.withQueuedAutoPhaseNotice(message: String): LobbyUiState =
        if (
            autoPhaseNoticeText == message ||
            message in autoPhaseNoticeQueue
        ) {
            this
        } else if (autoPhaseNoticeText == null) {
            copy(autoPhaseNoticeText = message)
        } else {
            copy(autoPhaseNoticeQueue = autoPhaseNoticeQueue + message)
        }

    fun updateLobbyCode(lobbyCode: String) {
        _state.update { it.copy(lobbyCode = lobbyCode.uppercase()) }
    }

    fun setJoining(isJoining: Boolean) {
        _state.update { it.copy(isJoining = isJoining) }
    }

    fun connect() {
        manualDisconnectRequested = false
        val snapshot = state.value
        if (snapshot.playerName.isBlank()) {
            _state.update { it.copy(errorText = config.errorPlayerNameRequired) }
            return
        }
        if (snapshot.isConnected || snapshot.isConnecting || snapshot.isReconnecting) {
            return
        }
        if (canReconnect(snapshot)) {
            beginReconnect(snapshot)
            return
        }

        scope.launch {
            _state.update {
                it.copy(
                    isConnecting = true,
                    isReconnecting = false,
                    statusText = config.statusConnecting,
                    errorText = null,
                )
            }

            runCatching {
                network.connect(snapshot.serverUrl)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        isReconnecting = false,
                        statusText = config.statusConnectionFailed,
                        errorText = error.message ?: config.errorUnknown,
                    )
                }
                clearPendingLobbyAction()
            }
        }
    }

    /**
     * Stößt die Wiederverbindung aus dem laufenden Spiel heraus erneut an.
     *
     * Der Button im Spiel darf keinen normalen Verbindungsaufbau starten, weil
     * dieser ohne alte Session nicht sicher zur laufenden Lobby zurückfindet.
     */
    fun retryReconnect() {
        manualDisconnectRequested = false
        val snapshot = state.value

        if (snapshot.isConnected) {
            if (snapshot.gameState.isDesynced || snapshot.gameState.lastSyncError != null) {
                requestGameCatchUp(
                    reason = GameStateCatchUpReason.AFTER_RECONNECT,
                    syncMessage = "Spielstand wird synchronisiert.",
                )
            }
            return
        }

        if (snapshot.isConnecting || snapshot.isReconnecting || awaitingReconnectResponse) {
            _state.update {
                it.copy(
                    isReconnecting = true,
                    statusText = config.statusReconnecting,
                    errorText = null,
                )
            }
            return
        }

        if (canReconnect(snapshot)) {
            beginReconnect(snapshot)
            return
        }

        _state.update {
            it.copy(
                isConnected = false,
                isConnecting = false,
                isReconnecting = false,
                statusText = config.statusReconnectFailed,
                errorText = config.errorReconnectTokenMissing,
            )
        }
        clearPendingLobbyAction()
    }

    fun disconnect() {
        manualDisconnectRequested = true
        cancelReconnect()
        scope.launch {
            runCatching { network.disconnect(config.disconnectReason) }
        }
    }

    fun createLobby(onLobbyReady: (String) -> Unit) {
        val snapshot = state.value
        if (snapshot.playerName.isBlank()) {
            _state.update { it.copy(errorText = config.errorPlayerNameRequired) }
            return
        }

        pendingCreateCallback = onLobbyReady
        pendingJoinCallback = null
        resetLobbyMembers()
        if (!snapshot.isConnected) {
            pendingLobbyAction = PendingLobbyAction.CREATE
            connect()
            return
        }

        submitCreateLobbyRequest()
    }

    fun joinLobby(onLobbyReady: (String) -> Unit) {
        val snapshot = state.value
        if (snapshot.playerName.isBlank()) {
            _state.update { it.copy(errorText = config.errorPlayerNameRequired) }
            return
        }
        if (snapshot.lobbyCode.length != config.lobbyCodeLength) {
            _state.update { it.copy(errorText = config.errorLobbyCodeLength) }
            return
        }

        pendingJoinCallback = onLobbyReady
        pendingCreateCallback = null
        resetLobbyMembers()
        if (!snapshot.isConnected) {
            pendingLobbyAction = PendingLobbyAction.JOIN
            connect()
            return
        }

        submitJoinLobbyRequest(snapshot)
    }

    fun close() {
        cancelReconnect()
        cancelDelayedAutoPhaseAdvance()
        cancelDelayedAutoAttackContinuation()
        network.close()
    }

    fun leaveLobby() {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode
        suppressNextAttackBoundaryNotice = false
        cancelDelayedAutoPhaseAdvance()
        cancelDelayedAutoAttackContinuation()
        if (lobbyCode != null) {
            scope.launch {
                runCatching {
                    sendCommand(
                        LobbyCommand(
                            key = LobbyCommandKey.LEAVE_LOBBY,
                            payload =
                                LeaveLobbyRequest(
                                    lobbyCode = parseLobbyCode(lobbyCode),
                                ),
                            retryPolicy = LobbyRetryPolicy.SAFE_ONCE,
                        ),
                        trackPending = false,
                    )
                }
            }
        }
        _state.update {
            it.copy(
                activeLobbyCode = null,
                isHost = false,
                playerNames = emptyList(),
                players = emptyList(),
                ownPlayerId = null,
                gameStarted = false,
                sessionToken = snapshot.sessionToken.takeIf { snapshot.isConnected },
                gameState = GameUiState().withAutoAttackPreference(it.autoAttackEnabled),
                pendingCommandKeys = emptySet(),
                autoPhaseNoticeText = null,
                autoPhaseNoticeQueue = emptyList(),
            )
        }
        reconnectSessionStore.clearSession()
        clearPendingLobbyAction()
        playersById.clear()
    }

    fun startGame() {
        val lobbyCode = state.value.activeLobbyCode
        if (lobbyCode == null) {
            _state.update { it.copy(errorText = config.errorLobbyMissing) }
            return
        }

        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.START_GAME,
                        payload = StartGameRequest(lobbyCode = parseLobbyCode(lobbyCode)),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorStartGameFailed)
                }
            }
        }
    }

    /**
     * Fordert einen autoritativen Full Snapshot für die aktuelle Lobby an.
     *
     * @param reason Grund für den Catch-up, damit der Server und Tests den Pfad
     * nachvollziehen können
     * @param syncMessage optionale UI-Meldung während der Synchronisierung
     */
    fun requestGameCatchUp(
        reason: GameStateCatchUpReason = GameStateCatchUpReason.AFTER_RECONNECT,
        syncMessage: String? = null,
    ) {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode ?: return

        _state.update {
            it.copy(
                gameState =
                    it.gameState.copy(
                        isCatchingUp = true,
                        isDesynced = reason != GameStateCatchUpReason.AFTER_RECONNECT,
                        lastSyncError = syncMessage,
                    ),
            )
        }
        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.CATCH_UP,
                        payload =
                            GameStateCatchUpRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                clientStateVersion = snapshot.gameState.stateVersion,
                                reason = reason,
                            ),
                        retryPolicy = LobbyRetryPolicy.SAFE_ONCE,
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                _state.update {
                    it.copy(
                        errorText = error.message ?: config.errorCatchUpFailed,
                        gameState =
                            it.gameState.copy(
                                isCatchingUp = false,
                                lastSyncError = error.message ?: config.errorCatchUpFailed,
                            ),
                    )
                }
            }
        }
    }

    /**
     * Lädt den privaten Spielerteil nach.
     *
     * Der öffentliche Snapshot enthält Karte, Besitzer, Truppen und TurnState.
     * Handkarten und geheime Ziele sind dagegen Spieler-spezifisch und werden
     * getrennt angefordert, damit private Daten nicht in Broadcasts landen.
     */
    fun requestPrivateGameState() {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode ?: return
        val playerId = snapshot.ownPlayerId ?: return

        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.PRIVATE_STATE,
                        payload =
                            GameStatePrivateGetRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                playerId = playerId,
                            ),
                        retryPolicy = LobbyRetryPolicy.SAFE_ONCE,
                    ),
                keepPendingUntilResponse = true,
            )
        }
    }

    fun refreshGameState() {
        val lobbyCode = state.value.activeLobbyCode
        if (lobbyCode == null) {
            _state.update { it.copy(errorText = config.errorLobbyMissing) }
            return
        }

        _state.update {
            it.copy(
                gameState =
                    it.gameState.copy(
                        isCatchingUp = true,
                        isDesynced = false,
                        lastSyncError = null,
                    ),
            )
        }
        requestMapSnapshot()
        requestTurnState()
        requestPrivateGameState()
    }

    private fun requestMapSnapshot() {
        val lobbyCode = state.value.activeLobbyCode ?: return
        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.MAP_GET,
                        payload = MapGetRequest(lobbyCode = parseLobbyCode(lobbyCode)),
                        retryPolicy = LobbyRetryPolicy.SAFE_ONCE,
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                updateGameError(error.message ?: config.errorMapGetFailed)
            }
        }
    }

    private fun requestTurnState() {
        val lobbyCode = state.value.activeLobbyCode ?: return
        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.TURN_STATE_GET,
                        payload = TurnStateGetRequest(lobbyCode = parseLobbyCode(lobbyCode)),
                        retryPolicy = LobbyRetryPolicy.SAFE_ONCE,
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                updateGameError(error.message ?: config.errorTurnStateGetFailed)
            }
        }
    }

    fun advanceTurn() {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode
        val playerId = snapshot.ownPlayerId
        if (lobbyCode == null || playerId == null) {
            _state.update { it.copy(errorText = config.errorPlayerIdMissing) }
            return
        }
        if (!snapshot.gameState.canRequestTurnAdvance(playerId, snapshot.isConnected)) {
            _state.update { it.copy(errorText = config.errorTurnAdvanceNotAllowed) }
            return
        }

        sendTurnAdvanceRequest(snapshot, lobbyCode, playerId)
    }

    private fun sendTurnAdvanceRequest(
        snapshot: LobbyUiState,
        lobbyCode: String,
        playerId: PlayerId,
    ) {
        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.TURN_ADVANCE,
                        payload =
                            TurnAdvanceRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                playerId = playerId,
                                expectedPhase = snapshot.gameState.turnPhase,
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorTurnAdvanceFailed)
                }
            }
        }
    }

    /**
     * Beendet Phasen automatisch, wenn lokal keine sinnvolle Aktion mehr möglich ist.
     *
     * Das betrifft drei UX-Fälle:
     * - Reinforcement wird verzögert bestätigt, wenn der aktive Spieler keine
     *   offenen Verstärkungen mehr besitzt.
     * - Attack wartet auf den autoritativen Serverwechsel, weil der Server nach
     *   einem Kampf selbst verzögert prüft, ob noch Angriffe möglich sind.
     * - Fortify wird nach einem bestätigten Move oder ohne gültigen
     *   Verbindungspfad verzögert weitergeschaltet, weil die Phase fachlich nur
     *   eine Verschiebung erlaubt.
     *
     * Reinforcement und Fortify nutzen dieselben Requests wie die sichtbaren
     * Buttons. Pending Keys verhindern doppelte Requests, falls Server-Response
     * und öffentliche Deltas sehr eng hintereinander eintreffen.
     *
     * @param delayBeforeAdvance ob vor Notice und Request zuerst ein visuelles
     * Delay geplant werden soll
     */
    private fun maybeAdvanceCurrentPhaseAutomatically(delayBeforeAdvance: Boolean = true) {
        val snapshot = state.value
        if (
            snapshot.autoPhaseNoticeText != null ||
            delayedAutoPhaseAdvanceJob != null
        ) {
            return
        }
        val playerId = snapshot.ownPlayerId ?: return
        if (!snapshot.gameState.canUseGameActions(playerId, snapshot.isConnected)) {
            return
        }

        when (snapshot.gameState.turnPhase) {
            TurnPhase.REINFORCEMENTS ->
                maybeAutoConfirmReinforcements(snapshot, playerId, delayBeforeAdvance)
            TurnPhase.ATTACK ->
                maybeAutoConfirmAttack(snapshot, playerId)
            TurnPhase.FORTIFY ->
                maybeAutoAdvanceFortify(snapshot, playerId, delayBeforeAdvance)
            TurnPhase.DRAW_CARD ->
                maybeAutoAdvanceDrawCard(snapshot, playerId, delayBeforeAdvance)
            else -> Unit
        }
    }

    private fun maybeAutoConfirmReinforcements(
        snapshot: LobbyUiState,
        playerId: PlayerId,
        delayBeforeAdvance: Boolean,
    ) {
        if (
            LobbyCommandKey.PLACE_REINFORCEMENTS in snapshot.pendingCommandKeys ||
            LobbyCommandKey.CONFIRM_REINFORCEMENTS_DONE in snapshot.pendingCommandKeys ||
            !snapshot.gameState.canConfirmReinforcementsDone(playerId, snapshot.isConnected)
        ) {
            return
        }
        if (delayBeforeAdvance) {
            scheduleAutoPhaseAdvanceAfterVisualDelay()
            return
        }
        enqueueAutoPhaseNotice(AUTO_PHASE_REINFORCEMENTS_DONE_NOTICE)
        confirmReinforcementsDone()
    }

    private fun maybeAutoConfirmAttack(
        snapshot: LobbyUiState,
        playerId: PlayerId,
    ) {
        if (
            LobbyCommandKey.ATTACK in snapshot.pendingCommandKeys ||
            LobbyCommandKey.CONFIRM_ATTACK_DONE in snapshot.pendingCommandKeys ||
            snapshot.gameState.territoryStates.isEmpty() ||
            snapshot.gameState.adjacentTerritoryIds.isEmpty() ||
            snapshot.gameState.hasAvailableAttack(playerId)
        ) {
            return
        }
        /*
         * Der Server beendet leere Angriffsphasen autoritativ mit eigenem Delay.
         * Ein zusätzlicher Client-Confirm würde gegen diesen Timer rennen und
         * kann nach dem serverseitigen Wechsel nur noch ein Fehler-Popup erzeugen.
         */
    }

    private fun maybeAutoAdvanceFortify(
        snapshot: LobbyUiState,
        playerId: PlayerId,
        delayBeforeAdvance: Boolean,
    ) {
        if (
            LobbyCommandKey.FORTIFY_MOVE in snapshot.pendingCommandKeys ||
            LobbyCommandKey.TURN_ADVANCE in snapshot.pendingCommandKeys ||
            !snapshot.gameState.canRequestTurnAdvance(playerId, snapshot.isConnected) ||
            snapshot.gameState.territoryStates.isEmpty() ||
            snapshot.gameState.adjacentTerritoryIds.isEmpty()
        ) {
            return
        }
        val noticeText =
            when {
                snapshot.gameState.fortifyState.hasMoved -> AUTO_PHASE_FORTIFY_MOVED_NOTICE
                !snapshot.gameState.hasAvailableFortify(playerId) -> AUTO_PHASE_FORTIFY_EMPTY_NOTICE
                else -> return
            }
        if (delayBeforeAdvance) {
            scheduleAutoPhaseAdvanceAfterVisualDelay()
            return
        }
        enqueueAutoPhaseNotice(noticeText)
        advanceTurn()
    }

    private fun maybeAutoAdvanceDrawCard(
        snapshot: LobbyUiState,
        playerId: PlayerId,
        delayBeforeAdvance: Boolean,
    ) {
        val lobbyCode = snapshot.activeLobbyCode ?: return
        if (
            LobbyCommandKey.TURN_ADVANCE in snapshot.pendingCommandKeys ||
            snapshot.gameState.turnPhase != TurnPhase.DRAW_CARD ||
            !snapshot.gameState.canUseGameActions(playerId, snapshot.isConnected)
        ) {
            return
        }
        if (delayBeforeAdvance) {
            scheduleAutoPhaseAdvanceAfterVisualDelay()
            return
        }
        enqueueAutoPhaseNotice(AUTO_PHASE_DRAW_CARD_DONE_NOTICE)
        sendTurnAdvanceRequest(snapshot, lobbyCode, playerId)
    }

    /**
     * Wartet vor jedem automatischen Phasenabschluss auf ein sichtbares
     * Zeitfenster, damit Kartenänderungen nachvollziehbar bleiben.
     *
     * @param delayMillis Dauer, für die die aktuelle Kartenlage stehen bleibt.
     */
    private fun scheduleAutoPhaseAdvanceAfterVisualDelay(
        delayMillis: Long = AUTO_PHASE_ADVANCE_DELAY_MILLIS,
    ) {
        val deadlineMillis = System.currentTimeMillis() + delayMillis
        delayedAutoPhaseAdvanceJob?.cancel()
        delayedAutoPhaseAdvanceDeadlineMillis = deadlineMillis
        delayedAutoPhaseAdvanceJob =
            scope.launch {
                delay(delayMillis)
                val deferredPhaseState = deferredOwnAttackPhaseState
                clearDelayedAutoPhaseAdvance()
                deferredOwnAttackPhaseState = null
                if (deferredPhaseState != null) {
                    applyDeferredOwnAttackPhaseState(deferredPhaseState)
                } else {
                    maybeAdvanceCurrentPhaseAutomatically(delayBeforeAdvance = false)
                }
            }
    }

    /**
     * Wartet den restlichen Result-Delay ab, bevor eine vom Server bereits
     * gelieferte Attack-Boundary angewendet wird.
     *
     * @param payload serverseitiger Phasenwechsel nach dem Angriff
     * @param remainingDelayMillis noch offene Zeit des sichtbaren Kampfergebnisses
     */
    private fun scheduleAttackBoundaryAfterVisualDelay(
        payload: PhaseBoundaryEvent,
        remainingDelayMillis: Long,
    ) {
        deferredOwnAttackPhaseBoundary = payload
        deferredOwnAttackPhaseState = null
        delayedAutoPhaseAdvanceJob?.cancel()
        delayedAutoPhaseAdvanceJob =
            scope.launch {
                delay(remainingDelayMillis)
                clearDelayedAutoPhaseAdvance()
                deferredOwnAttackPhaseBoundary = null
                applyPhaseBoundaryWithOptionalAttackNotice(
                    payload = payload,
                    showServerAttackAutoNotice = true,
                )
            }
    }

    /**
     * Berechnet, wie lange das sichtbare Kampfergebnis noch stehen bleiben soll.
     *
     * @param nowMillis aktueller Zeitstempel in Millisekunden
     */
    private fun remainingAutoPhaseAdvanceDelayMillis(
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val deadlineMillis = delayedAutoPhaseAdvanceDeadlineMillis ?: return null
        return (deadlineMillis - nowMillis).coerceAtLeast(0L)
    }

    private fun cancelDelayedAutoPhaseAdvance() {
        delayedAutoPhaseAdvanceJob?.cancel()
        clearDelayedAutoPhaseAdvance()
        deferredOwnAttackPhaseState = null
        deferredOwnAttackPhaseBoundary = null
    }

    private fun clearDelayedAutoPhaseAdvance() {
        delayedAutoPhaseAdvanceJob = null
        delayedAutoPhaseAdvanceDeadlineMillis = null
    }

    /**
     * Ändert die lokal vorbereitete Anzahl der nächsten Verstärkungsplatzierung.
     *
     * Dabei wird noch kein Request gesendet. Erst der Klick auf "Platzieren"
     * übernimmt die aktuell ausgewählte Anzahl in einen autoritativen
     * Serverrequest.
     */
    fun adjustReinforcementPlacementAmount(delta: Int) {
        _state.update {
            it.copy(
                gameState =
                    ClientGameStateReducer.adjustReinforcementPlacementAmount(
                        current = it.gameState,
                        delta = delta,
                    ),
            )
        }
    }

    /**
     * Sendet eine Verstärkungsplatzierung für das ausgewählte eigene Gebiet.
     *
     * Die Kartenoberfläche arbeitet mit den Android-Region-IDs der Hitmap,
     * während das Protokoll die stabilen Territory-IDs aus `shared` benötigt.
     * Die Übersetzung wird deshalb erst an dieser Request-Grenze durchgeführt.
     * Der lokale Restpool wird nicht optimistisch reduziert; er folgt dem
     * serverseitigen `PendingReinforcementsChangedEvent`.
     */
    fun placeReinforcements() {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode
        val playerId = snapshot.ownPlayerId
        if (lobbyCode == null || playerId == null) {
            _state.update { it.copy(errorText = config.errorPlayerIdMissing) }
            return
        }
        val regionId = snapshot.gameState.selectedRegionId
        if (regionId == null) {
            _state.update { it.copy(errorText = config.errorReinforcementTargetMissing) }
            return
        }
        if (!snapshot.gameState.canPlaceReinforcements(playerId, snapshot.isConnected)) {
            _state.update { it.copy(errorText = config.errorReinforcementsNotAllowed) }
            return
        }

        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.PLACE_REINFORCEMENTS,
                        payload =
                            PlaceReinforcementsRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                playerId = playerId,
                                placements =
                                    listOf(
                                        TerritoryPlacement(
                                            territoryId =
                                                GameMapTerritoryMapper.toTerritoryId(
                                                    regionId,
                                                ),
                                            amount =
                                                snapshot.gameState.reinforcementPlacementAmount,
                                        ),
                                    ),
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorPlaceReinforcementsFailed)
                }
            }
        }
    }

    fun claimCheatReinforcementBonus() {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode
        val playerId = snapshot.ownPlayerId
        if (lobbyCode == null || playerId == null) {
            _state.update { it.copy(errorText = config.errorPlayerIdMissing) }
            return
        }
        if (!snapshot.gameState.canManageReinforcements(playerId, snapshot.isConnected)) {
            _state.update { it.copy(errorText = config.errorReinforcementsNotAllowed) }
            return
        }

        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.CLAIM_CHEAT_REINFORCEMENT_BONUS,
                        payload =
                            ClaimCheatReinforcementBonusRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                playerId = playerId,
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorReinforcementsNotAllowed)
                }
            }
        }
    }

    fun reportCheat(accusedPlayerId: PlayerId) {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode
        val reporterPlayerId = snapshot.ownPlayerId
        if (lobbyCode == null || reporterPlayerId == null) {
            _state.update { it.copy(errorText = config.errorPlayerIdMissing) }
            return
        }
        /*
         * Selbstmeldungen blockiere ich schon in der App. Der Server prüft das
         * später trotzdem noch einmal, damit manipulierte Requests nicht durchkommen.
         */
        if (reporterPlayerId == accusedPlayerId) {
            _state.update { it.copy(errorText = "Du kannst dich nicht selbst melden.") }
            return
        }

        scope.launch {
            _state.update { it.copy(cheatReportNoticeText = null) }
            /*
             * Die App schickt nur, wer wen meldet. Ob die Meldung stimmt,
             * entscheidet der Server anhand des aktuellen Meldefensters.
             */
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.REPORT_CHEAT,
                        payload =
                            ReportCheatRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                reporterPlayerId = reporterPlayerId,
                                accusedPlayerId = accusedPlayerId,
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                _state.update {
                    it.copy(
                        errorText = error.message ?: "Cheat-Meldung konnte nicht gesendet werden.",
                    )
                }
            }
        }
    }

    /**
     * Bestätigt die Verstärkungsphase, sobald der Restpool vollständig verbraucht ist.
     *
     * Die UI ruft diese Methode über den gewöhnlichen Button "Phase beenden"
     * auf. Der separate Request verhindert, dass der Client eine
     * Verstärkungsphase durch einen generischen Phasenwechsel überspringen
     * könnte, solange der Server noch Truppen oder einen Pflicht-Trade erwartet.
     */
    fun confirmReinforcementsDone() {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode
        val playerId = snapshot.ownPlayerId
        if (lobbyCode == null || playerId == null) {
            _state.update { it.copy(errorText = config.errorPlayerIdMissing) }
            return
        }
        if (!snapshot.gameState.canConfirmReinforcementsDone(playerId, snapshot.isConnected)) {
            _state.update { it.copy(errorText = config.errorReinforcementsNotAllowed) }
            return
        }

        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.CONFIRM_REINFORCEMENTS_DONE,
                        payload =
                            ConfirmReinforcementsDoneRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                playerId = playerId,
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorConfirmReinforcementsFailed)
                }
            }
        }
    }

    /** Ändert lokal die Truppenanzahl für die nächste Angriffsaktion. */
    fun adjustAttackTroops(delta: Int) {
        _state.update {
            it.copy(
                gameState =
                    ClientGameStateReducer.adjustAttackTroops(
                        current = it.gameState,
                        delta = delta,
                    ),
            )
        }
    }

    /** Ändert lokal die gewünschte Besetzung für ein möglicherweise erobertes Zielgebiet. */
    fun adjustMoveAfterCapture(delta: Int) {
        _state.update {
            it.copy(
                gameState =
                    ClientGameStateReducer.adjustMoveAfterCapture(
                        current = it.gameState,
                        delta = delta,
                    ),
            )
        }
    }

    /**
     * Merkt den Auto-Angriff fuer die aktuelle Attack-Auswahl vor oder bricht ihn ab.
     *
     * Der Toggle selbst sendet keinen Request. Erst der normale Angreifen-Button
     * friert die Route ein und startet die Auto-Sequenz mit einzelnen
     * serverautoritativen AttackRequests.
     */
    fun setAutoAttackEnabled(enabled: Boolean) {
        playerNameStore.saveAutoAttackEnabled(enabled)
        if (!enabled) {
            stopAutoAttack(
                statusText = null,
                keepEnabled = false,
            )
            return
        }

        _state.update {
            val autoAttack = it.gameState.attackState.autoAttack
            val canStart =
                !autoAttack.isRunning &&
                    it.gameState.canStartAutoAttack(it.ownPlayerId, it.isConnected)
            it.copy(
                autoAttackEnabled = true,
                errorText = if (canStart) null else it.errorText,
                gameState =
                    it.gameState.withAutoAttackPreference(
                        enabled = true,
                        statusText = null,
                        errorText = null,
                    ),
            )
        }
    }

    private fun beginAutoAttack(
        snapshot: LobbyUiState,
        fromRegionId: String,
        toRegionId: String,
    ) {
        val playerId = snapshot.ownPlayerId
        if (!snapshot.gameState.canStartAutoAttack(playerId, snapshot.isConnected)) {
            _state.update { it.copy(errorText = config.errorAttackNotAllowed) }
            return
        }

        val intent =
            AutoAttackIntent(
                fromTerritoryId = GameMapTerritoryMapper.toTerritoryId(fromRegionId),
                toTerritoryId = GameMapTerritoryMapper.toTerritoryId(toRegionId),
                attackTroops = snapshot.gameState.attackState.attackTroops,
                moveAfterCapture = snapshot.gameState.attackState.moveAfterCapture,
            )
        _state.update {
            it.copy(
                errorText = null,
                gameState =
                    it.gameState.copy(
                        attackState =
                            it.gameState.attackState.copy(
                                autoAttack =
                                    it.gameState.attackState.autoAttack.copy(
                                        intent = intent,
                                        isEnabled = true,
                                        isAwaitingResult = false,
                                        pendingRequestId = null,
                                        statusText = config.autoAttackStarted,
                                        errorText = null,
                                    ),
                            ),
                    ),
            )
        }
        continueAutoAttackIfReady()
    }

    /**
     * Sendet eine einzelne Angriffsabsicht fuer die ausgewaehlten Gebiete.
     *
     * Trefferwuerfe, Verluste und Eroberungen werden nicht lokal berechnet. Die
     * Anzeige folgt ausschliesslich dem anschliessend empfangenen Battle-Event.
     */
    fun attack() {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode
        val playerId = snapshot.ownPlayerId
        if (lobbyCode == null || playerId == null) {
            _state.update { it.copy(errorText = config.errorPlayerIdMissing) }
            return
        }
        val fromRegionId = snapshot.gameState.selectionFromRegionId
        val toRegionId = snapshot.gameState.selectionToRegionId
        if (fromRegionId == null || toRegionId == null) {
            _state.update { it.copy(errorText = config.errorAttackSelectionMissing) }
            return
        }
        if (!snapshot.gameState.canSubmitAttack(playerId, snapshot.isConnected)) {
            _state.update { it.copy(errorText = config.errorAttackNotAllowed) }
            return
        }
        if (snapshot.gameState.attackState.autoAttack.isEnabled) {
            beginAutoAttack(
                snapshot = snapshot,
                fromRegionId = fromRegionId,
                toRegionId = toRegionId,
            )
            return
        }

        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.ATTACK,
                        payload =
                            AttackRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                playerId = playerId,
                                fromTerritoryId =
                                    GameMapTerritoryMapper.toTerritoryId(fromRegionId),
                                toTerritoryId = GameMapTerritoryMapper.toTerritoryId(toRegionId),
                                attackTroops = snapshot.gameState.attackState.attackTroops,
                                moveAfterCapture = snapshot.gameState.attackState.moveAfterCapture,
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorAttackFailed)
                }
            }
        }
    }

    private fun continueAutoAttackIfReady(
        authoritativeGameState: GameUiState = state.value.gameState,
        delayBeforeRequest: Boolean = false,
    ) {
        val snapshot = state.value
        val autoAttack = authoritativeGameState.attackState.autoAttack
        if (!autoAttack.isEnabled || autoAttack.intent == null) {
            cancelDelayedAutoAttackContinuation()
            return
        }

        val stopReason = autoAttackStopReason(snapshot, authoritativeGameState)
        if (stopReason != null) {
            stopAutoAttack(statusText = stopReason)
            return
        }

        if (delayBeforeRequest) {
            scheduleAutoAttackContinuationAfterVisualDelay()
            return
        }

        if (
            autoAttack.isAwaitingResult ||
            LobbyCommandKey.ATTACK in snapshot.pendingCommandKeys
        ) {
            return
        }
        if (!delayBeforeRequest && delayedAutoAttackContinuationJob?.isActive == true) {
            return
        }

        val plan = autoAttackRequestPlan(snapshot, authoritativeGameState)
        if (plan == null) {
            stopAutoAttack(statusText = config.autoAttackStoppedInvalidTarget)
            return
        }

        cancelDelayedAutoAttackContinuation()
        val requestId = nextAutoAttackRequestId()
        markAutoAttackRequestSent(requestId)
        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.ATTACK,
                        payload =
                            AttackRequest(
                                lobbyCode = plan.lobbyCode,
                                playerId = plan.playerId,
                                fromTerritoryId = plan.fromTerritoryId,
                                toTerritoryId = plan.toTerritoryId,
                                attackTroops = plan.attackTroops,
                                moveAfterCapture = plan.moveAfterCapture,
                                requestId = requestId,
                            ),
                    ),
                keepPendingUntilResponse = true,
                trackPending = false,
            ).onFailure { error ->
                val message = error.message ?: config.errorAttackFailed
                stopAutoAttack(
                    statusText = config.autoAttackStoppedRejected,
                    errorText = message,
                )
                updateGameError(message)
            }
        }
    }

    private fun autoAttackStopReason(
        snapshot: LobbyUiState,
        gameState: GameUiState,
    ): String? {
        val playerId = snapshot.ownPlayerId ?: return config.errorPlayerIdMissing
        val intent =
            gameState.attackState.autoAttack.intent
                ?: return config.autoAttackStoppedInvalidTarget

        if (!snapshot.isConnected) {
            return config.autoAttackStoppedConnectionLost
        }
        if (gameState.turnPhase != TurnPhase.ATTACK) {
            return config.autoAttackStoppedPhaseChanged
        }
        if (gameState.activePlayerId != playerId) {
            return config.autoAttackStoppedActivePlayerChanged
        }

        val source = gameState.territoryStates[intent.fromTerritoryId]
        val target = gameState.territoryStates[intent.toTerritoryId]
        if (target?.ownerId == playerId) {
            return config.autoAttackStoppedCaptured
        }
        if (source?.ownerId != playerId) {
            return config.autoAttackStoppedInvalidTarget
        }
        if ((source.troopCount - 1) < intent.attackTroops) {
            return config.autoAttackStoppedSourceWeak
        }
        val isAdjacent =
            intent.toTerritoryId in
                gameState.adjacentTerritoryIds[intent.fromTerritoryId].orEmpty()
        if (
            target?.ownerId == null ||
            target.troopCount <= 0 ||
            !isAdjacent
        ) {
            return config.autoAttackStoppedInvalidTarget
        }

        return null
    }

    private fun autoAttackRequestPlan(
        snapshot: LobbyUiState,
        gameState: GameUiState,
    ): AutoAttackRequestPlan? {
        val lobbyCode = snapshot.activeLobbyCode?.let(::parseLobbyCode) ?: return null
        val playerId = snapshot.ownPlayerId ?: return null
        val intent = gameState.attackState.autoAttack.intent ?: return null
        val source = gameState.territoryStates[intent.fromTerritoryId] ?: return null
        val maxAttackTroops = source.troopCount - 1
        if (maxAttackTroops < intent.attackTroops || intent.attackTroops < MIN_ATTACK_TROOPS) {
            return null
        }
        val minimumMoveAfterCapture = minimumOccupyingTroopsForAttack(intent.attackTroops)
        if (intent.moveAfterCapture !in minimumMoveAfterCapture..intent.attackTroops) {
            return null
        }

        return AutoAttackRequestPlan(
            lobbyCode = lobbyCode,
            playerId = playerId,
            fromTerritoryId = intent.fromTerritoryId,
            toTerritoryId = intent.toTerritoryId,
            attackTroops = intent.attackTroops,
            moveAfterCapture = intent.moveAfterCapture,
        )
    }

    private fun markAutoAttackRequestSent(requestId: String) {
        _state.update {
            val autoAttack = it.gameState.attackState.autoAttack
            it.copy(
                gameState =
                    it.gameState.copy(
                        attackState =
                            it.gameState.attackState.copy(
                                autoAttack =
                                    autoAttack.copy(
                                        isAwaitingResult = true,
                                        pendingRequestId = requestId,
                                        statusText = config.autoAttackPending,
                                        errorText = null,
                                    ),
                            ),
                    ),
            )
        }
    }

    private fun stopAutoAttack(
        statusText: String?,
        errorText: String? = null,
        keepEnabled: Boolean = true,
    ) {
        cancelDelayedAutoAttackContinuation()
        if (!keepEnabled) {
            playerNameStore.saveAutoAttackEnabled(false)
        }
        _state.update {
            val autoAttack = it.gameState.attackState.autoAttack
            val nextIsEnabled = keepEnabled && it.autoAttackEnabled
            if (isAutoAttackStopNoOp(
                    autoAttack,
                    it.autoAttackEnabled,
                    nextIsEnabled,
                    statusText,
                    errorText,
                )
            ) {
                it
            } else {
                it.withStoppedAutoAttack(nextIsEnabled, statusText, errorText)
            }
        }
    }

    /**
     * Prüft, ob ein [stopAutoAttack] den Zustand unverändert ließe, um redundante
     * State-Emissionen zu vermeiden. No-op nur, wenn der Auto-Angriff schon
     * gestoppt ist (weder aktiv noch auf ein Ergebnis wartend) und entweder kein
     * Intent/Fehler mehr ansteht oder Status-/Fehlertext bereits identisch sind.
     */
    private fun isAutoAttackStopNoOp(
        autoAttack: AutoAttackUiState,
        autoAttackEnabled: Boolean,
        nextIsEnabled: Boolean,
        statusText: String?,
        errorText: String?,
    ): Boolean {
        if (autoAttack.isEnabled || autoAttack.isAwaitingResult) {
            return false
        }
        val enabledUnchanged = autoAttackEnabled == nextIsEnabled
        val nothingPending =
            autoAttack.intent == null && errorText == null && enabledUnchanged
        val textsUnchanged =
            autoAttack.statusText == statusText &&
                autoAttack.errorText == errorText &&
                enabledUnchanged
        return nothingPending || textsUnchanged
    }

    /**
     * Baut den gestoppten Auto-Angriff-Zustand. Lief gerade eine Sequenz, wird die
     * (jetzt veraltete) Gebietsauswahl mitgelöscht, damit sich das Angriffspanel
     * automatisch schließt. Bei einer Eroberung erledigt das bereits der Reducer;
     * hier werden alle übrigen Stoppgründe (z. B. zu schwache Quelle) abgedeckt.
     */
    private fun LobbyUiState.withStoppedAutoAttack(
        nextIsEnabled: Boolean,
        statusText: String?,
        errorText: String?,
    ): LobbyUiState {
        val autoAttack = gameState.attackState.autoAttack
        // Bei laufender Sequenz die veraltete Gebietsauswahl verwerfen.
        val keep = autoAttack.intent == null && !autoAttack.isAwaitingResult
        return copy(
            autoAttackEnabled = nextIsEnabled,
            gameState =
                gameState.copy(
                    selectedRegionId = gameState.selectedRegionId.takeIf { keep },
                    selectionFromRegionId = gameState.selectionFromRegionId.takeIf { keep },
                    selectionToRegionId = gameState.selectionToRegionId.takeIf { keep },
                    attackState =
                        gameState.attackState.copy(
                            autoAttack =
                                AutoAttackUiState(
                                    isEnabled = nextIsEnabled,
                                    statusText = statusText,
                                    errorText = errorText,
                                ),
                        ),
                ),
        )
    }

    private fun shouldStopAutoAttackForError(requestId: String?): Boolean {
        val autoAttack = state.value.gameState.attackState.autoAttack
        return autoAttack.isEnabled &&
            autoAttack.intent != null &&
            (requestId == null || autoAttack.pendingRequestId == requestId)
    }

    private fun nextAutoAttackRequestId(): String {
        autoAttackRequestSequence += 1
        return "auto-attack-$autoAttackRequestSequence"
    }

    private fun scheduleAutoAttackContinuationAfterVisualDelay() {
        if (delayedAutoAttackContinuationJob?.isActive == true) {
            return
        }
        delayedAutoAttackContinuationJob =
            scope.launch {
                delay(AUTO_ATTACK_CONTINUATION_DELAY_MILLIS)
                clearDelayedAutoAttackContinuation()
                continueAutoAttackIfReady()
            }
    }

    private fun cancelDelayedAutoAttackContinuation() {
        delayedAutoAttackContinuationJob?.cancel()
        clearDelayedAutoAttackContinuation()
    }

    private fun clearDelayedAutoAttackContinuation() {
        delayedAutoAttackContinuationJob = null
    }

    /** Beendet die Angriffsphase ueber den dafuer vorgesehenen Serverrequest. */
    fun confirmAttackDone() {
        confirmAttackDone(suppressAutoBoundaryNotice = true)
    }

    private fun confirmAttackDone(suppressAutoBoundaryNotice: Boolean) {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode
        val playerId = snapshot.ownPlayerId
        if (lobbyCode == null || playerId == null) {
            _state.update { it.copy(errorText = config.errorPlayerIdMissing) }
            return
        }
        if (!snapshot.gameState.canConfirmAttackDone(playerId, snapshot.isConnected)) {
            _state.update { it.copy(errorText = config.errorAttackNotAllowed) }
            return
        }

        if (
            suppressAutoBoundaryNotice &&
            applyDeferredOwnAttackPhaseStateForManualConfirm()
        ) {
            return
        }

        if (suppressAutoBoundaryNotice) {
            suppressNextAttackBoundaryNotice = true
        }
        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.CONFIRM_ATTACK_DONE,
                        payload =
                            ConfirmAttackDoneRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                playerId = playerId,
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                if (suppressAutoBoundaryNotice) {
                    suppressNextAttackBoundaryNotice = false
                }
                _state.update {
                    it.copy(errorText = error.message ?: config.errorConfirmAttackFailed)
                }
            }
        }
    }

    /**
     * Übernimmt einen bereits empfangenen, aber wegen Result-Delay noch
     * zurückgehaltenen Attack-Phasenwechsel bei manuellem Abschluss.
     *
     * Der Serverzustand ist in diesem Fall schon autoritativ weiter als die
     * sichtbare Topbar. Ein zusätzlicher Confirm wäre veraltet und könnte nur
     * eine Fehlerantwort erzeugen.
     *
     * @return `true`, wenn ein zurückgehaltener Serverzustand übernommen wurde
     */
    private fun applyDeferredOwnAttackPhaseStateForManualConfirm(): Boolean {
        val deferredBoundary = deferredOwnAttackPhaseBoundary
        if (deferredBoundary != null) {
            delayedAutoPhaseAdvanceJob?.cancel()
            clearDelayedAutoPhaseAdvance()
            deferredOwnAttackPhaseBoundary = null
            deferredOwnAttackPhaseState = null
            manuallyConsumedAttackBoundaryStateVersion = deferredBoundary.stateVersion
            applyPhaseBoundaryWithOptionalAttackNotice(
                payload = deferredBoundary,
                showServerAttackAutoNotice = false,
            )
            return true
        }

        val deferredPhaseState = deferredOwnAttackPhaseState ?: return false
        delayedAutoPhaseAdvanceJob?.cancel()
        clearDelayedAutoPhaseAdvance()
        deferredOwnAttackPhaseState = null
        manuallyConsumedAttackBoundaryStateVersion = deferredPhaseState.stateVersion
        _state.update { current ->
            current.copy(
                gameState = deferredPhaseState.withAutoAttackPreference(current.autoAttackEnabled),
            )
        }
        maybeAdvanceCurrentPhaseAutomatically()
        return true
    }

    /**
     * Ändert lokal die Truppenanzahl für die einmalige Fortify-Bewegung.
     *
     * @param delta relative Änderung aus dem Fortify-Slider
     */
    fun adjustFortifyTroops(delta: Int) {
        _state.update {
            it.copy(
                gameState =
                    ClientGameStateReducer.adjustFortifyTroops(
                        current = it.gameState,
                        delta = delta,
                    ),
            )
        }
    }

    /**
     * Sendet eine Fortify-Bewegung zwischen zwei verbundenen eigenen Gebieten.
     *
     * Die lokale Auswahl prüft bereits Eigentum und Verbindungspfad über die
     * Map-Definition. Der Server bleibt dennoch die autoritative Quelle und
     * liefert die tatsächlichen Truppenänderungen anschließend als öffentliche
     * Deltas zurück.
     */
    fun fortifyMove() {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode
        val playerId = snapshot.ownPlayerId
        if (lobbyCode == null || playerId == null) {
            _state.update { it.copy(errorText = config.errorPlayerIdMissing) }
            return
        }
        val fromRegionId = snapshot.gameState.selectionFromRegionId
        val toRegionId = snapshot.gameState.selectionToRegionId
        if (fromRegionId == null || toRegionId == null) {
            _state.update { it.copy(errorText = config.errorFortifySelectionMissing) }
            return
        }
        if (!snapshot.gameState.canSubmitFortifyMove(playerId, snapshot.isConnected)) {
            _state.update { it.copy(errorText = config.errorFortifyNotAllowed) }
            return
        }

        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.FORTIFY_MOVE,
                        payload =
                            FortifyMoveRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                playerId = playerId,
                                fromTerritoryId =
                                    GameMapTerritoryMapper.toTerritoryId(fromRegionId),
                                toTerritoryId = GameMapTerritoryMapper.toTerritoryId(toRegionId),
                                troopCount = snapshot.gameState.fortifyState.troopCount,
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorFortifyFailed)
                }
            }
        }
    }

    /**
     * Wählt eine private Karte für den möglichen Eintausch aus oder ab.
     *
     * Die Auswahl bleibt rein lokal und enthält ausschließlich IDs aus dem
     * privaten Snapshot. Nach einem Trade-in bereinigt das private Hand-Event
     * nicht mehr vorhandene ausgewählte Karten automatisch.
     */
    fun toggleTradeInCard(cardId: CardId) {
        _state.update {
            it.copy(gameState = ClientGameStateReducer.toggleTradeInCard(it.gameState, cardId))
        }
    }

    /**
     * Sendet das lokal ausgewählte Kartenset für die Verstärkungsphase.
     *
     * Der Request trägt nur die ausgewählten Karten-IDs. Bonushöhe, Gültigkeit
     * des Sets und ein möglicher Pflicht-Trade werden vollständig serverseitig
     * berechnet und danach über Grant-/Hand-Events zurückgespiegelt.
     */
    fun tradeInCards() {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode
        val playerId = snapshot.ownPlayerId
        if (lobbyCode == null || playerId == null) {
            _state.update { it.copy(errorText = config.errorPlayerIdMissing) }
            return
        }
        if (!snapshot.gameState.canTradeInCards(playerId, snapshot.isConnected)) {
            _state.update { it.copy(errorText = config.errorTradeInNotAllowed) }
            return
        }

        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.TRADE_IN_CARDS,
                        payload =
                            TradeInCardsRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                                playerId = playerId,
                                cardIds = snapshot.gameState.selectedTradeInCardIds.toList(),
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorTradeInFailed)
                }
            }
        }
    }

    fun selectGameRegion(regionId: String) {
        /*
         * Die Karte liefert nur eine Android-Region-ID. Die fachliche Validierung
         * passiert im Reducer, weil dort TurnPhase, Owner und lokaler Spieler
         * gemeinsam verfügbar sind.
         */
        _state.update {
            it.copy(
                gameState =
                    ClientGameStateReducer.selectRegion(
                        current = it.gameState,
                        regionId = regionId,
                        localPlayerId = it.ownPlayerId,
                    ),
            )
        }
    }

    fun toggleCards() {
        _state.update {
            it.copy(gameState = ClientGameStateReducer.toggleCards(it.gameState))
        }
    }

    private suspend fun sendCommand(
        command: LobbyCommand,
        keepPendingUntilResponse: Boolean = false,
        trackPending: Boolean = true,
    ): Result<Unit> {
        if (trackPending && state.value.pendingCommandKeys.contains(command.key)) {
            return Result.success(Unit)
        }

        if (trackPending) {
            _state.update {
                it.copy(pendingCommandKeys = it.pendingCommandKeys + command.key)
            }
        }
        val result =
            runCatching {
                commandDispatcher.send(command)
            }
        if (trackPending && (!keepPendingUntilResponse || result.isFailure)) {
            clearPendingCommand(command.key)
        }
        return result
    }

    private fun clearPendingCommand(commandKey: LobbyCommandKey) {
        _state.update {
            it.copy(pendingCommandKeys = it.pendingCommandKeys - commandKey)
        }
    }

    /**
     * Behandelt den technischen Verbindungsaufbau aus Sicht der Lobby.
     *
     * Bei einem normalen Connect darf der Pending-Create- oder Join-Flow sofort
     * weiterlaufen. Bei einem Reconnect bleibt die UI dagegen gesperrt, bis der
     * Server die alte Session explizit wieder an diese neue Verbindung gebunden
     * hat.
     */
    private fun handleConnected() {
        val token = reconnectSessionToken
        if (awaitingReconnectResponse && token != null) {
            _state.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false,
                    isReconnecting = true,
                    statusText = config.statusReconnecting,
                    errorText = null,
                )
            }
            submitReconnectRequest(token)
            return
        }

        _state.update {
            it.copy(
                isConnected = true,
                isConnecting = false,
                isReconnecting = false,
                statusText = config.statusConnected,
                errorText = null,
            )
        }
        executePendingLobbyActionIfAny()
        if (state.value.gameStarted) {
            requestGameCatchUp(
                reason = GameStateCatchUpReason.AFTER_RECONNECT,
                syncMessage =
                    "Verbindung wiederhergestellt. " +
                        "Spielstand wird geprüft.",
            )
        }
    }

    /**
     * Startet nach einem unerwarteten Verbindungsverlust den fachlichen
     * Reconnect, solange noch ein Session-Token und ein Lobby-Kontext vorhanden
     * sind. Manuelles Trennen bleibt davon ausgenommen, damit der Disconnect-
     * Button keine automatische Wiederverbindung auslöst.
     */
    private fun handleConnectionLost(
        statusText: String,
        errorText: String?,
        gameErrorText: String,
    ) {
        val snapshot = state.value
        _state.update {
            it.copy(
                isConnected = false,
                isConnecting = false,
                statusText = statusText,
                errorText = errorText,
                pendingCommandKeys = emptySet(),
                globalPlayerCount = null,
                gameState =
                    it.gameState.copy(
                        lastSyncError =
                            if (it.gameStarted) {
                                gameErrorText
                            } else {
                                it.gameState.lastSyncError
                            },
                    ),
            )
        }

        stopAutoAttack(statusText = config.autoAttackStoppedConnectionLost)

        if (!canReconnect(snapshot)) {
            clearPendingLobbyAction()
            return
        }

        beginReconnect(snapshot)
    }

    private fun canReconnect(snapshot: LobbyUiState): Boolean =
        !manualDisconnectRequested &&
            snapshot.sessionToken != null &&
            (snapshot.activeLobbyCode != null || snapshot.gameStarted)

    /**
     * Merkt sich den alten Token vor dem technischen Neuverbinden.
     *
     * Der Server sendet bei jedem neuen WebSocket zunächst einen frischen
     * provisorischen Token. Für den eigentlichen Reconnect muss der Client aber
     * den alten Token verwenden, weil nur daran Spieler und Lobby hängen.
     */
    private fun beginReconnect(snapshot: LobbyUiState) {
        if (reconnectJob?.isActive == true || awaitingReconnectResponse) {
            return
        }

        val token =
            runCatching { SessionToken(snapshot.sessionToken ?: "") }
                .getOrNull()
        if (token == null) {
            _state.update {
                it.copy(
                    isReconnecting = false,
                    statusText = config.statusReconnectFailed,
                    errorText = config.errorReconnectTokenMissing,
                )
            }
            clearPendingLobbyAction()
            return
        }

        reconnectSessionToken = token
        awaitingReconnectResponse = true
        reconnectJob =
            scope.launch {
                repeat(config.reconnectMaxAttempts) { attempt ->
                    if (attempt > 0) {
                        delay(config.reconnectRetryDelayMillis)
                    }

                    _state.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = true,
                            isReconnecting = true,
                            statusText = config.statusReconnecting,
                            errorText = null,
                        )
                    }

                    runCatching {
                        network.connect(snapshot.serverUrl)
                    }.onSuccess {
                        return@launch
                    }.onFailure { error ->
                        _state.update {
                            it.copy(
                                isConnected = false,
                                isConnecting = false,
                                isReconnecting = true,
                                errorText = error.message ?: config.errorUnknown,
                            )
                        }
                    }
                }

                awaitingReconnectResponse = false
                reconnectSessionToken = null
                _state.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        isReconnecting = false,
                        statusText = config.statusReconnectFailed,
                        errorText = config.errorReconnectFailed,
                    )
                }
                clearPendingLobbyAction()
            }
    }

    private fun submitReconnectRequest(sessionToken: SessionToken) {
        scope.launch {
            runCatching {
                network.sendPayload(ReconnectRequest(sessionToken))
            }.onFailure { error ->
                awaitingReconnectResponse = false
                reconnectSessionToken = null
                _state.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        isReconnecting = false,
                        statusText = config.statusReconnectFailed,
                        errorText = error.message ?: config.errorReconnectFailed,
                    )
                }
                clearPendingLobbyAction()
            }
        }
    }

    private fun handleConnectionResponse(payload: ConnectionResponse) {
        /*
         * Bei einem normalen Connect ist die ConnectionResponse die Quelle für
         * den ersten stabilen Token. Während eines Reconnects sendet der Server
         * aber zunächst ebenfalls eine technische ConnectionResponse für die
         * neue WebSocket-Verbindung. Dieser provisorische Token darf den alten
         * fachlichen Token nicht überschreiben, sonst würde der eigentliche
         * ReconnectRequest mit der falschen Session laufen.
         */
        if (!awaitingReconnectResponse) {
            val sessionToken = payload.sessionToken.value
            reconnectSessionStore.saveSessionToken(sessionToken)
            _state.update { it.copy(sessionToken = sessionToken) }
        }
    }

    private fun handleReconnectResponse(payload: ReconnectResponse) {
        val shouldRequestCatchUp = state.value.gameStarted
        val pendingSessionToken = reconnectSessionToken?.value ?: state.value.sessionToken
        awaitingReconnectResponse = false
        reconnectSessionToken = null

        if (!payload.success) {
            /*
             * TOKEN_INVALID, TOKEN_EXPIRED und TOKEN_REVOKED bedeuten, dass der
             * lokal gespeicherte Schlüssel nicht mehr zu einer Server-Session
             * gehört. Der Client löscht ihn sofort, damit der nächste App-Start
             * nicht wieder in denselben kaputten Reconnect läuft.
             */
            reconnectSessionStore.clearSession()
            _state.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false,
                    isReconnecting = false,
                    statusText = config.statusReconnectFailed,
                    errorText = payload.errorCode?.name ?: config.errorReconnectFailed,
                    sessionToken = null,
                    activeLobbyCode = null,
                    ownPlayerId = null,
                    isHost = false,
                    players = emptyList(),
                    playerNames = emptyList(),
                    gameStarted = false,
                    gameState = GameUiState().withAutoAttackPreference(it.autoAttackEnabled),
                )
            }
            playersById.clear()
            clearPendingLobbyAction()
            return
        }

        val confirmedSessionToken =
            requireNotNull(pendingSessionToken) {
                "Successful reconnect response requires a known session token."
            }
        reconnectSessionStore.saveSessionToken(confirmedSessionToken)

        _state.update {
            it.copy(
                isConnected = true,
                isConnecting = false,
                isReconnecting = false,
                statusText = config.statusConnected,
                errorText = null,
                sessionToken = confirmedSessionToken,
                activeLobbyCode = payload.lobbyCodeValueOrNull(it.activeLobbyCode),
                lobbyCode = payload.lobbyCodeValueOr(it.lobbyCode),
                playerName = payload.playerDisplayNameOr(it.playerName),
                ownPlayerId = payload.playerIdOr(it.ownPlayerId),
                gameState =
                    it.gameState.copy(
                        isCatchingUp = it.gameStarted,
                        isDesynced = false,
                        lastSyncError = null,
                    ),
            )
        }

        if (shouldRequestCatchUp) {
            requestGameCatchUp(
                reason = GameStateCatchUpReason.AFTER_RECONNECT,
                syncMessage =
                    "Session wiederhergestellt. " +
                        "Spielstand wird synchronisiert.",
            )
        }
    }

    private fun ReconnectResponse.lobbyCodeValueOrNull(fallback: String?): String? {
        val restoredLobbyCode = lobbyCode
        return if (restoredLobbyCode == null) {
            fallback
        } else {
            restoredLobbyCode.value
        }
    }

    private fun ReconnectResponse.lobbyCodeValueOr(fallback: String): String {
        val restoredLobbyCode = lobbyCode
        return if (restoredLobbyCode == null) {
            fallback
        } else {
            restoredLobbyCode.value
        }
    }

    private fun ReconnectResponse.playerDisplayNameOr(fallback: String): String {
        val restoredPlayerDisplayName = playerDisplayName
        return if (restoredPlayerDisplayName == null) {
            normalizePlayerDisplayName(fallback)
        } else {
            normalizePlayerDisplayName(restoredPlayerDisplayName)
        }
    }

    private fun ReconnectResponse.playerIdOr(fallback: PlayerId?): PlayerId? {
        val restoredPlayerId = playerId
        return if (restoredPlayerId == null) {
            fallback
        } else {
            restoredPlayerId
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectSessionToken = null
        awaitingReconnectResponse = false
        _state.update {
            it.copy(
                isConnecting = false,
                isReconnecting = false,
            )
        }
    }

    private fun handlePacketDecodeFailure(error: Throwable) {
        val message = error.message ?: config.errorPacketDecode
        _state.update {
            it.copy(
                errorText = message,
                gameState =
                    if (it.gameStarted) {
                        it.gameState.copy(
                            isCatchingUp = true,
                            isDesynced = true,
                            lastSyncError = config.errorPacketDecode,
                        )
                    } else {
                        it.gameState
                    },
            )
        }
        if (state.value.gameStarted) {
            requestGameCatchUp(
                reason = GameStateCatchUpReason.OUT_OF_ORDER,
                syncMessage = config.errorPacketDecode,
            )
        }
    }

    private fun handlePayload(payload: NetworkMessagePayload) {
        /*
         * Der Controller ist der zentrale Demultiplexer für Server-Payloads:
         * technische Connection-Payloads bleiben hier, Lobby-Events pflegen die
         * Playerliste und Game-Payloads werden an den GameStateReducer delegiert.
         */
        if (handleReinforcementPayload(payload)) {
            return
        }
        if (handleAttackPayload(payload)) {
            return
        }
        if (handleFortifyPayload(payload)) {
            return
        }
        when (payload) {
            is ConnectionResponse -> handleConnectionResponse(payload)
            is ReconnectResponse -> handleReconnectResponse(payload)
            is CreateLobbyResponse -> {
                clearPendingCommand(LobbyCommandKey.CREATE_LOBBY)
                handleCreateLobbyResponse(payload)
            }
            is CreateLobbyErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.CREATE_LOBBY)
                pendingCreateCallback = null
                _state.update { it.copy(errorText = payload.reason) }
            }
            is JoinLobbyResponse -> {
                val isPendingJoin =
                    state.value.pendingCommandKeys.contains(LobbyCommandKey.JOIN_LOBBY)
                clearPendingCommand(LobbyCommandKey.JOIN_LOBBY)
                if (isPendingJoin) {
                    handleJoinLobbyResponse(payload)
                }
            }
            is ReportCheatResponse -> {
                clearPendingCommand(LobbyCommandKey.REPORT_CHEAT)
                val reportNotice =
                    if (payload.correct) {
                        "Deine Meldung war korrekt. Du erhältst in deiner nächsten " +
                            "Verstärkungsphase +3 Truppen. Der gemeldete Spieler erhält " +
                            "dann 0 Truppen."
                    } else {
                        "Du hast jemanden falsch verdächtigt. In deiner nächsten " +
                            "Verstärkungsphase werden dir 3 Truppen abgezogen."
                    }
                _state.update {
                    it.copy(
                        errorText = null,
                        cheatReportNoticeText = reportNotice,
                    )
                }
            }
            is ReportCheatErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.REPORT_CHEAT)
                updateGameError(payload.reason)
            }
            is JoinLobbyErrorResponse -> {
                val isPendingJoin =
                    state.value.pendingCommandKeys.contains(LobbyCommandKey.JOIN_LOBBY)
                clearPendingCommand(LobbyCommandKey.JOIN_LOBBY)
                pendingJoinCallback = null
                if (isPendingJoin) {
                    _state.update { it.copy(errorText = payload.reason) }
                }
            }
            is PlayerJoinedLobbyEvent -> handlePlayerJoined(payload)
            is ConnectionStatusUpdateEvent -> handleConnectionStatusUpdate(payload)
            is PlayerConnectionLostEvent -> handlePlayerConnectionLost(payload)
            is PlayerLeftLobbyEvent -> {
                playersById.remove(payload.playerId.value)
                markLobbyHost(payload.newHost)
                publishPlayers()
            }
            is PlayerKickedLobbyEvent -> {
                playersById.remove(payload.targetPlayerId.value)
                publishPlayers()
            }
            is PlayerCountUpdateEvent -> {
                _state.update { it.copy(onlinePlayerCount = payload.playerCount) }
            }
            is GlobalPlayerCountEvent -> {
                _state.update { it.copy(globalPlayerCount = payload.playerCount) }
            }
            is CharacterSelectResponse -> updateCharacter(payload.characterId)
            is CharacterSelectErrorResponse -> {
                _state.update { it.copy(characterSelectError = payload.reason) }
            }
            is CharacterSelectedBroadcast -> {
                val existing = playersById[payload.playerId.value]
                if (existing != null) {
                    playersById[payload.playerId.value] =
                        existing.copy(characterId = payload.characterId)
                    publishPlayers()
                }
            }
            is StartGameResponse -> {
                clearPendingCommand(LobbyCommandKey.START_GAME)
                handleStartGameResponse(payload)
            }
            is StartGameErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.START_GAME)
                updateGameError(payload.reason)
            }
            is GameStartedEvent -> handleGameStarted(payload)
            is MapGetResponse -> {
                clearPendingCommand(LobbyCommandKey.MAP_GET)
                applyGameState { current, players ->
                    ClientGameStateReducer.applyMapGetResponse(current, payload, players)
                }
            }
            is MapGetErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.MAP_GET)
                updateGameError(GameErrorTextMapper.map(payload))
            }
            is GameStateCatchUpResponse -> {
                clearPendingCommand(LobbyCommandKey.CATCH_UP)
                applyGameState { current, players ->
                    ClientGameStateReducer.applyCatchUpResponse(current, payload, players)
                }
                requestPrivateGameState()
            }
            is GameStateCatchUpErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.CATCH_UP)
                updateGameError(GameErrorTextMapper.map(payload))
            }
            is GameStateSnapshotBroadcast ->
                applyGameState { current, players ->
                    ClientGameStateReducer.applySnapshotBroadcast(current, payload, players)
                }
            is MatchEndedBroadcastEvent ->
                applyGameState { current, _ ->
                    ClientGameStateReducer.applyMatchEndedBroadcast(current, payload)
                }
            is GameStateDeltaEvent -> handleGameStateDelta(payload)
            is PhaseBoundaryEvent -> handlePhaseBoundary(payload)
            is TurnStateGetResponse -> {
                clearPendingCommand(LobbyCommandKey.TURN_STATE_GET)
                applyGameState { current, _ ->
                    ClientGameStateReducer.applyTurnStateGetResponse(current, payload)
                }
            }
            is TurnAdvanceResponse -> {
                clearPendingCommand(LobbyCommandKey.TURN_ADVANCE)
                _state.update { it.copy(errorText = null) }
            }
            is TurnAdvanceErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.TURN_ADVANCE)
                updateGameError(GameErrorTextMapper.map(payload))
            }
            is TurnStateGetErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.TURN_STATE_GET)
                updateGameError(GameErrorTextMapper.map(payload))
            }
            is GameStatePrivateGetResponse -> {
                clearPendingCommand(LobbyCommandKey.PRIVATE_STATE)
                applyGameState { current, _ ->
                    ClientGameStateReducer.applyPrivateGetResponse(current, payload)
                }
            }
            is GameStatePrivateGetErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.PRIVATE_STATE)
                updateGameError(GameErrorTextMapper.map(payload))
            }
            is PlayerHandUpdatedEvent ->
                applyGameState { current, _ ->
                    ClientGameStateReducer.applyPlayerHandUpdatedEvent(current, payload)
                }
        }
    }

    /**
     * Verarbeitet die drei Requests der Verstärkungsphase außerhalb des allgemeinen
     * Payload-Routers. Dadurch bleibt der zentrale Router auf fachliche Gruppen
     * begrenzt, während Success- und Error-Antworten weiterhin identisch wirken.
     *
     * @return `true`, wenn [payload] vollständig verarbeitet wurde
     */
    private fun handleReinforcementPayload(payload: NetworkMessagePayload): Boolean =
        when (payload) {
            is PlaceReinforcementsResponse -> {
                clearPendingCommand(LobbyCommandKey.PLACE_REINFORCEMENTS)
                _state.update { it.copy(errorText = null) }
                maybeAdvanceCurrentPhaseAutomatically()
                true
            }
            is ClaimCheatReinforcementBonusResponse -> {
                clearPendingCommand(LobbyCommandKey.CLAIM_CHEAT_REINFORCEMENT_BONUS)
                _state.update { it.copy(errorText = null) }
                true
            }
            is ClaimCheatReinforcementBonusErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.CLAIM_CHEAT_REINFORCEMENT_BONUS)
                updateTemporaryCheatError(GameErrorTextMapper.map(payload))
                true
            }
            is PlaceReinforcementsErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.PLACE_REINFORCEMENTS)
                updateGameError(GameErrorTextMapper.map(payload))
                true
            }
            is ConfirmReinforcementsDoneResponse -> {
                clearPendingCommand(LobbyCommandKey.CONFIRM_REINFORCEMENTS_DONE)
                _state.update { it.copy(errorText = null) }
                true
            }
            is ConfirmReinforcementsDoneErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.CONFIRM_REINFORCEMENTS_DONE)
                updateGameError(GameErrorTextMapper.map(payload))
                true
            }
            is TradeInCardsResponse -> {
                clearPendingCommand(LobbyCommandKey.TRADE_IN_CARDS)
                _state.update { it.copy(errorText = null) }
                true
            }
            is TradeInCardsErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.TRADE_IN_CARDS)
                updateGameError(GameErrorTextMapper.map(payload))
                true
            }
            else -> false
        }

    /**
     * Verarbeitet Antworten der Angriffsphase; das eigentliche Kampfergebnis
     * erreicht den Reducer separat als öffentliches Delta-Event.
     */
    private fun handleAttackPayload(payload: NetworkMessagePayload): Boolean =
        when (payload) {
            is AttackResponse -> {
                clearPendingCommand(LobbyCommandKey.ATTACK)
                _state.update { it.copy(errorText = null) }
                continueAutoAttackIfReady()
                true
            }
            is AttackErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.ATTACK)
                val message = GameErrorTextMapper.map(payload)
                if (shouldStopAutoAttackForError(payload.requestId)) {
                    stopAutoAttack(
                        statusText = config.autoAttackStoppedRejected,
                        errorText = message,
                    )
                }
                updateGameError(message)
                true
            }
            is ConfirmAttackDoneResponse -> {
                clearPendingCommand(LobbyCommandKey.CONFIRM_ATTACK_DONE)
                _state.update { it.copy(errorText = null) }
                true
            }
            is ConfirmAttackDoneErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.CONFIRM_ATTACK_DONE)
                suppressNextAttackBoundaryNotice = false
                updateGameError(GameErrorTextMapper.map(payload))
                true
            }
            else -> false
        }

    /**
     * Verarbeitet Antworten der Fortify-Phase.
     *
     * Der Success-Payload bestätigt nur die Annahme des Moves. Die Truppenstände
     * werden über nachfolgende öffentliche Territory-Deltas aktualisiert.
     */
    private fun handleFortifyPayload(payload: NetworkMessagePayload): Boolean =
        when (payload) {
            is FortifyMoveResponse -> {
                clearPendingCommand(LobbyCommandKey.FORTIFY_MOVE)
                applyGameState { current, _ ->
                    ClientGameStateReducer.applyFortifyMoveAccepted(current)
                }
                _state.update { it.copy(errorText = null) }
                true
            }
            is FortifyMoveErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.FORTIFY_MOVE)
                updateGameError(GameErrorTextMapper.map(payload))
                true
            }
            else -> false
        }

    private fun handlePlayerJoined(payload: PlayerJoinedLobbyEvent) {
        val existingPlayer = playersById[payload.playerId.value]
        val displayName = normalizePlayerDisplayName(payload.playerDisplayName)
        playersById[payload.playerId.value] =
            LobbyPlayerUi(
                playerId = payload.playerId,
                displayName = displayName,
                isHost = payload.isHost,
                connectionStatus = ConnectionStatus.CONNECTED,
                characterId = existingPlayer?.characterId,
            )

        var shouldSelectSavedCharacter = false
        _state.update { current ->
            val ownPlayerId =
                current.ownPlayerId
                    ?: payload.playerId.takeIf { displayName == current.playerName }
            shouldSelectSavedCharacter = current.ownPlayerId == null && ownPlayerId != null
            current.copy(ownPlayerId = ownPlayerId)
        }
        publishPlayers()
        if (shouldSelectSavedCharacter) {
            selectSavedCharacterForLobbyIfPossible()
        }
    }

    /**
     * Meldet den lokal gespeicherten Charakter automatisch an die Lobby.
     *
     * Der eigene PlayerId-Wert ist erst nach dem Join-Broadcast bekannt. Ohne
     * diesen Nachlauf sehen andere Clients den initial gespeicherten Charakter
     * erst nach einem manuellen erneuten Speichern.
     */
    private fun selectSavedCharacterForLobbyIfPossible() {
        val snapshot = state.value
        val ownPlayerId = snapshot.ownPlayerId ?: return
        val takenCharacterIds =
            snapshot.players
                .filter { it.playerId != ownPlayerId }
                .mapNotNull { it.characterId }
                .toSet()
        val preferredCharacterId =
            snapshot.characterId?.takeIf { characterId ->
                characterId.isNotBlank() &&
                    Characters.byId(characterId) != null &&
                    characterId !in takenCharacterIds
            }
        val characterId =
            preferredCharacterId
                ?: Characters.all.firstOrNull { it.id !in takenCharacterIds }?.id
                ?: return
        selectCharacter(characterId)
    }

    /**
     * Markiert einen Lobby-Spieler als getrennt, ohne ihn aus der Liste zu entfernen.
     *
     * Verlassene Spieler können im laufenden Spiel weiterhin Territorien besitzen.
     * Die UI braucht daher die Spieler-ID weiter für Farben, Anzeige und
     * Angriffslogik gegen zurückgelassene Gebiete.
     *
     * @param payload Serverevent zum verlorenen Spieler.
     */
    private fun handlePlayerConnectionLost(payload: PlayerConnectionLostEvent) {
        val existingPlayer = playersById[payload.playerId.value] ?: return
        playersById[payload.playerId.value] =
            existingPlayer.copy(connectionStatus = ConnectionStatus.DISCONNECTED)
        publishPlayers()
    }

    /**
     * Übernimmt den autoritativen Verbindungsstatus eines bekannten Lobby-Spielers.
     *
     * @param payload Serverevent mit dem aktuellen Status.
     */
    private fun handleConnectionStatusUpdate(payload: ConnectionStatusUpdateEvent) {
        val existingPlayer = playersById[payload.playerId.value] ?: return
        playersById[payload.playerId.value] =
            existingPlayer.copy(connectionStatus = payload.status)
        publishPlayers()
    }

    /**
     * Spiegelt den autoritativen Host in die lokale Playerliste.
     *
     * @param newHost neuer Host aus Serverevent oder `null`, wenn keiner gesetzt ist.
     */
    private fun markLobbyHost(newHost: PlayerId?) {
        if (newHost == null) {
            return
        }

        playersById.replaceAll { _, player ->
            player.copy(isHost = player.playerId == newHost)
        }
    }

    private fun handleStartGameResponse(payload: StartGameResponse) {
        if (payload.success) {
            _state.update { it.copy(errorText = null) }
        }
    }

    private fun handleGameStarted(payload: GameStartedEvent) {
        state.value.sessionToken?.let(reconnectSessionStore::saveSessionToken)
        reconnectSessionStore.saveWasGameStarted(true)
        _state.update {
            it.copy(
                gameStarted = true,
                activeLobbyCode = payload.lobbyCode.value,
                gameState =
                    it.gameState.copy(
                        isStarted = true,
                        isCatchingUp = true,
                        lastSyncError = null,
                    ),
                errorText = null,
            )
        }
        requestGameCatchUp(GameStateCatchUpReason.AFTER_RECONNECT)
    }

    private fun handleGameStateDelta(payload: GameStateDeltaEvent) {
        val snapshot = state.value
        val ownPlayerId = snapshot.ownPlayerId
        val containsOwnAttackResult =
            payload.events
                .filterIsInstance<AttackResolvedBroadcastEvent>()
                .any { it.attackerPlayerId == ownPlayerId }
        val result =
            ClientGameStateReducer.applyDelta(
                current = snapshot.gameState,
                delta = payload,
                players = snapshot.players,
            )
        val authoritativeGameState =
            result.state.withAutoAttackPreference(snapshot.autoAttackEnabled)
        val isOwnAttackAutoBoundary =
            isOwnAttackAutoBoundaryDelta(
                snapshot = snapshot,
                payload = payload,
                nextState = authoritativeGameState,
            )
        /*
         * Der Server sendet beim Attack-Auto-Skip die Boundary vor dem
         * TurnState-Delta. Wenn die Boundary bereits für das Result-Delay
         * geparkt ist, darf das nachlaufende Delta die Topbar nicht früher auf
         * Fortify umstellen.
         */
        val shouldKeepDelayedAttackBoundary =
            isOwnAttackAutoBoundary &&
                deferredOwnAttackPhaseBoundary != null &&
                delayedAutoPhaseAdvanceJob != null
        val shouldDelayOwnAttackPhaseState =
            (containsOwnAttackResult && isOwnAttackAutoBoundary) ||
                shouldKeepDelayedAttackBoundary
        val shouldShowOwnAttackAutoNotice =
            isOwnAttackAutoBoundary &&
                !shouldDelayOwnAttackPhaseState
        val visibleGameState =
            if (shouldDelayOwnAttackPhaseState) {
                deferredOwnAttackPhaseState = authoritativeGameState
                authoritativeGameState.copy(turnPhase = TurnPhase.ATTACK)
            } else {
                authoritativeGameState
            }

        if (shouldShowOwnAttackAutoNotice) {
            cancelDelayedAutoPhaseAdvance()
        }
        _state.update { current ->
            val updated = current.copy(gameState = visibleGameState)
            if (shouldShowOwnAttackAutoNotice) {
                updated.withQueuedAutoPhaseNotice(AUTO_PHASE_ATTACK_DONE_NOTICE)
            } else {
                updated
            }
        }
        when {
            containsOwnAttackResult -> scheduleAutoPhaseAdvanceAfterVisualDelay()
            shouldKeepDelayedAttackBoundary -> Unit
            shouldShowOwnAttackAutoNotice -> Unit
            else -> maybeAdvanceCurrentPhaseAutomatically()
        }
        if (result.needsCatchUp) {
            requestGameCatchUp(
                reason = GameStateCatchUpReason.MISSING_DELTA,
                syncMessage = result.state.lastSyncError,
            )
        } else {
            continueAutoAttackIfReady(
                authoritativeGameState = authoritativeGameState,
                delayBeforeRequest = containsOwnAttackResult,
            )
        }
    }

    /**
     * Erkennt den serverseitigen Auto-Wechsel von Attack nach Fortify im Delta.
     *
     * Der Server broadcastet bei einem automatischen Attack-Ende zuerst ein
     * `GameStateDeltaEvent` mit [TurnStateUpdatedEvent] und danach zusätzlich
     * das erklärende [PhaseBoundaryEvent]. Ohne diese Sonderbehandlung würde die
     * Topbar beim aktiven Angreifer bereits auf Fortify springen, bevor die
     * lokale Auto-Skip-Notice sichtbar wird.
     *
     * Manuelle Bestätigungen werden hier ausgeschlossen, weil der Spieler in
     * diesem Fall selbst auf "Phase beenden" geklickt hat und kein Auto-Popup
     * erwartet.
     *
     * @param snapshot lokaler Zustand vor Anwendung des Deltas
     * @param payload empfangenes öffentliches Delta
     * @param nextState bereits reduzierter Zielzustand aus dem Delta
     * @return `true`, wenn das Delta den eigenen automatischen Attack-Skip beschreibt
     */
    private fun isOwnAttackAutoBoundaryDelta(
        snapshot: LobbyUiState,
        payload: GameStateDeltaEvent,
        nextState: GameUiState,
    ): Boolean {
        val playerId = snapshot.ownPlayerId ?: return false
        if (
            suppressNextAttackBoundaryNotice ||
            snapshot.gameState.turnPhase != TurnPhase.ATTACK ||
            nextState.turnPhase != TurnPhase.FORTIFY ||
            nextState.activePlayerId != playerId
        ) {
            return false
        }

        return payload.events
            .filterIsInstance<TurnStateUpdatedEvent>()
            .any { event ->
                event.activePlayerId == playerId &&
                    event.turnPhase == TurnPhase.FORTIFY
            }
    }

    /**
     * Übernimmt einen im Attack-Delta zurückgehaltenen Phasenwechsel.
     *
     * Der Server kann das Kampfergebnis und den Wechsel auf Fortify im selben
     * Delta liefern. Für den angreifenden Client bleibt die sichtbare Phase
     * während des Result-Delays auf Angriff, damit die Topbar nicht vor dem
     * erklärenden Popup springt.
     *
     * @param gameState bereits reduzierter Zielzustand aus dem Serverdelta
     */
    private fun applyDeferredOwnAttackPhaseState(gameState: GameUiState) {
        _state.update { current ->
            current
                .copy(gameState = gameState)
                .withQueuedAutoPhaseNotice(AUTO_PHASE_ATTACK_DONE_NOTICE)
        }
    }

    private fun handlePhaseBoundary(payload: PhaseBoundaryEvent) {
        val snapshot = state.value
        if (payload.stateVersion < snapshot.gameState.stateVersion) {
            return
        }
        manuallyConsumedAttackBoundaryStateVersion
            ?.takeIf { consumedVersion -> payload.stateVersion > consumedVersion }
            ?.let { manuallyConsumedAttackBoundaryStateVersion = null }
        val isAttackBoundary =
            payload.previousPhase == TurnPhase.ATTACK &&
                payload.nextPhase == TurnPhase.FORTIFY
        val wasManuallyConsumedAttackBoundary =
            isAttackBoundary &&
                manuallyConsumedAttackBoundaryStateVersion == payload.stateVersion
        val shouldSuppressAttackBoundaryNotice =
            isAttackBoundary &&
                (suppressNextAttackBoundaryNotice || wasManuallyConsumedAttackBoundary)
        val isOwnAttackBoundary =
            isAttackBoundary && snapshot.ownPlayerId == payload.activePlayerId
        if (isOwnAttackBoundary && !shouldSuppressAttackBoundaryNotice) {
            val remainingDelayMillis = remainingAutoPhaseAdvanceDelayMillis()
            if (remainingDelayMillis != null && remainingDelayMillis > 0L) {
                scheduleAttackBoundaryAfterVisualDelay(
                    payload = payload,
                    remainingDelayMillis = remainingDelayMillis,
                )
                return
            }
        }
        cancelDelayedAutoPhaseAdvance()
        if (shouldSuppressAttackBoundaryNotice) {
            suppressNextAttackBoundaryNotice = false
            if (wasManuallyConsumedAttackBoundary) {
                manuallyConsumedAttackBoundaryStateVersion = null
            }
        }
        val shouldShowServerAttackAutoNotice =
            isOwnAttackBoundary &&
                !shouldSuppressAttackBoundaryNotice

        applyPhaseBoundaryWithOptionalAttackNotice(
            payload = payload,
            showServerAttackAutoNotice = shouldShowServerAttackAutoNotice,
        )
    }

    /**
     * Wendet einen Phasenwechsel an und zeigt nur bei eigenen Attack-Auto-Skips
     * die lokale UX-Notice.
     *
     * Notice und GameState werden atomar gesetzt. Dadurch kann die Topbar nicht
     * einen Frame früher die nächste Phase zeigen, während das erklärende Popup
     * noch fehlt.
     *
     * @param payload autoritativer Phasenwechsel vom Server
     * @param showServerAttackAutoNotice ob der eigene Client die Attack-Notice sehen soll
     */
    private fun applyPhaseBoundaryWithOptionalAttackNotice(
        payload: PhaseBoundaryEvent,
        showServerAttackAutoNotice: Boolean,
    ) {
        deferredOwnAttackPhaseState = null
        deferredOwnAttackPhaseBoundary = null
        _state.update { current ->
            val updated =
                current.copy(
                    gameState =
                        ClientGameStateReducer.applyPhaseBoundary(
                            current = current.gameState,
                            event = payload,
                        ).withAutoAttackPreference(current.autoAttackEnabled),
                )
            if (showServerAttackAutoNotice) {
                updated.withQueuedAutoPhaseNotice(AUTO_PHASE_ATTACK_DONE_NOTICE)
            } else {
                updated
            }
        }

        if (!showServerAttackAutoNotice) {
            maybeAdvanceCurrentPhaseAutomatically()
        }
        continueAutoAttackIfReady()
    }

    private fun applyGameState(
        runAutoAdvance: Boolean = true,
        reducer: (current: GameUiState, players: List<LobbyPlayerUi>) -> GameUiState,
    ) {
        var delayAutoAttackContinuation = false
        _state.update { current ->
            val previousAutoAttack = current.gameState.attackState.autoAttack
            val nextGameState =
                reducer(current.gameState, current.players)
                    .withAutoAttackPreference(current.autoAttackEnabled)
            delayAutoAttackContinuation =
                shouldDelayAutoAttackAfterAuthoritativeState(
                    previous = previousAutoAttack,
                    next = nextGameState.attackState.autoAttack,
                )
            current.copy(
                gameState = nextGameState,
            )
        }
        if (runAutoAdvance) {
            maybeAdvanceCurrentPhaseAutomatically()
        }
        continueAutoAttackIfReady(delayBeforeRequest = delayAutoAttackContinuation)
    }

    private fun shouldDelayAutoAttackAfterAuthoritativeState(
        previous: AutoAttackUiState,
        next: AutoAttackUiState,
    ): Boolean =
        previous.isEnabled &&
            previous.intent != null &&
            previous.isAwaitingResult &&
            next.isEnabled &&
            next.intent == previous.intent &&
            !next.isAwaitingResult

    private fun GameUiState.withAutoAttackPreference(
        enabled: Boolean,
        statusText: String? = attackState.autoAttack.statusText,
        errorText: String? = attackState.autoAttack.errorText,
    ): GameUiState {
        val nextAutoAttack =
            if (enabled) {
                attackState.autoAttack.copy(
                    isEnabled = true,
                    statusText = statusText,
                    errorText = errorText,
                )
            } else {
                AutoAttackUiState()
            }
        return copy(attackState = attackState.copy(autoAttack = nextAutoAttack))
    }

    private fun updateGameError(reason: String) {
        _state.update {
            it.copy(
                errorText = reason,
                gameState =
                    it.gameState.copy(
                        isCatchingUp = false,
                        lastSyncError = reason,
                    ),
            )
        }
    }

    // Cheat-Fehlermeldungen sollen nur 3 Sekunden sichtbar bleiben.
    private fun updateTemporaryCheatError(reason: String) {
        updateGameError(reason)
        clearCheatErrorJob?.cancel()
        clearCheatErrorJob =
            scope.launch {
                delay(3_000)
                _state.update { current ->
                    if (current.errorText == reason) {
                        current.copy(errorText = null)
                    } else {
                        current
                    }
                }
            }
    }

    private fun executePendingLobbyActionIfAny() {
        when (pendingLobbyAction) {
            PendingLobbyAction.CREATE -> {
                pendingLobbyAction = null
                submitCreateLobbyRequest()
            }
            PendingLobbyAction.JOIN -> {
                val snapshot = state.value
                if (snapshot.lobbyCode.length != config.lobbyCodeLength) {
                    pendingLobbyAction = null
                    pendingJoinCallback = null
                    _state.update { it.copy(errorText = config.errorLobbyCodeLength) }
                    return
                }
                pendingLobbyAction = null
                submitJoinLobbyRequest(snapshot)
            }
            null -> Unit
        }
    }

    private fun submitCreateLobbyRequest() {
        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.CREATE_LOBBY,
                        payload = CreateLobbyRequest,
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                pendingCreateCallback = null
                _state.update {
                    it.copy(errorText = error.message ?: config.errorCreateFailed)
                }
            }
        }
    }

    private fun submitJoinLobbyRequest(snapshot: LobbyUiState = state.value) {
        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.JOIN_LOBBY,
                        payload =
                            JoinLobbyRequest(
                                lobbyCode = parseLobbyCode(snapshot.lobbyCode),
                                playerDisplayName = normalizePlayerDisplayName(snapshot.playerName),
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                pendingJoinCallback = null
                _state.update {
                    it.copy(errorText = error.message ?: config.errorJoinFailed)
                }
            }
        }
    }

    private fun handleCreateLobbyResponse(payload: CreateLobbyResponse) {
        val lobbyCode = payload.lobbyCode.value

        _state.update {
            it.copy(
                lobbyCode = lobbyCode,
                activeLobbyCode = lobbyCode,
                isHost = true,
                errorText = null,
            )
        }

        scope.launch {
            sendCommand(
                command =
                    LobbyCommand(
                        key = LobbyCommandKey.JOIN_LOBBY,
                        payload =
                            JoinLobbyRequest(
                                lobbyCode = payload.lobbyCode,
                                playerDisplayName =
                                    normalizePlayerDisplayName(state.value.playerName),
                            ),
                    ),
                keepPendingUntilResponse = true,
            ).onFailure { error ->
                pendingCreateCallback = null
                _state.update {
                    it.copy(errorText = error.message ?: config.errorJoinFailed)
                }
            }
        }
    }

    private fun handleJoinLobbyResponse(payload: JoinLobbyResponse) {
        val joinedCode = payload.lobbyCode.value
        _state.update {
            it.copy(
                activeLobbyCode = joinedCode,
                lobbyCode = joinedCode,
                playerNames = ensureOwnPlayerName(it.playerNames, it.playerName),
                errorText = null,
            )
        }

        val createCallback = pendingCreateCallback
        if (createCallback != null) {
            pendingCreateCallback = null
            createCallback(joinedCode)
            return
        }

        val joinCallback = pendingJoinCallback
        if (joinCallback != null) {
            pendingJoinCallback = null
            _state.update { it.copy(isHost = false) }
            joinCallback(joinedCode)
        }
    }

    private fun parseLobbyCode(value: String) = LobbyCode(value.uppercase())

    private fun resetLobbyMembers() {
        playersById.clear()
        suppressNextAttackBoundaryNotice = false
        manuallyConsumedAttackBoundaryStateVersion = null
        cancelDelayedAutoPhaseAdvance()
        reconnectSessionStore.saveWasGameStarted(false)
        _state.update {
            it.copy(
                players = emptyList(),
                playerNames = emptyList(),
                ownPlayerId = null,
                gameStarted = false,
                gameState = GameUiState().withAutoAttackPreference(it.autoAttackEnabled),
                pendingCommandKeys = emptySet(),
                autoPhaseNoticeText = null,
                autoPhaseNoticeQueue = emptyList(),
            )
        }
    }

    private fun clearPendingLobbyAction() {
        pendingLobbyAction = null
        pendingCreateCallback = null
        pendingJoinCallback = null
        _state.update { it.copy(pendingCommandKeys = emptySet()) }
    }

    /**
     * Veröffentlicht die interne Player-Map in den Compose-State.
     *
     * Hostrechte werden aus den autoritativen Playerdaten neu berechnet. Dadurch
     * bekommt ein neuer Host nach einem Leave-Event sofort die passenden Buttons,
     * auch wenn das alte lokale Host-Flag noch auf dem vorherigen Wert stand.
     */
    private fun publishPlayers() {
        val players = playersById.values.toList()
        _state.update {
            val hasAuthoritativeHost = players.any(LobbyPlayerUi::isHost)
            val isOwnHost =
                if (hasAuthoritativeHost) {
                    it.ownPlayerId != null &&
                        players.any { player ->
                            player.playerId == it.ownPlayerId && player.isHost
                        }
                } else {
                    it.isHost
                }
            it.copy(
                isHost = isOwnHost,
                players = players,
                playerNames = players.map(LobbyPlayerUi::displayName),
                gameState = ClientGameStateReducer.applyPlayers(it.gameState, players),
            )
        }
    }

    private fun ensureOwnPlayerName(
        currentNames: List<String>,
        ownName: String,
    ): List<String> {
        val normalizedOwnName = normalizePlayerDisplayName(ownName)
        if (normalizedOwnName.isBlank()) {
            return currentNames
        }
        if (currentNames.contains(normalizedOwnName)) {
            return currentNames
        }
        return currentNames + normalizedOwnName
    }
}

private const val AUTO_PHASE_REINFORCEMENTS_DONE_NOTICE =
    "Keine Verstärkungen mehr verfügbar. Die Verstärkungsphase wird " +
        "automatisch beendet."
private const val AUTO_PHASE_ATTACK_DONE_NOTICE =
    "Keine Angriffe mehr möglich. Die Angriffsphase wird automatisch beendet."
private const val AUTO_PHASE_FORTIFY_MOVED_NOTICE =
    "Truppen wurden verschoben. Die Verschiebephase wird automatisch beendet."
private const val AUTO_PHASE_FORTIFY_EMPTY_NOTICE =
    "Keine Truppenverschiebung möglich. Die Verschiebephase wird " +
        "automatisch beendet."
private const val AUTO_PHASE_DRAW_CARD_DONE_NOTICE =
    "Karte wurde gezogen. Die Kartenphase wird automatisch beendet."
private const val AUTO_PHASE_ADVANCE_DELAY_MILLIS = 2_500L
private const val AUTO_ATTACK_CONTINUATION_DELAY_MILLIS = 500L
