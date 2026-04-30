package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.app.game.ClientGameStateReducer
import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.app.network.ClientNetwork
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpReason
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponse
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
 */
class LobbyController(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val network: ClientNetwork = ClientNetwork(scope),
    private val config: LobbyControllerConfig = LobbyControllerConfig(),
) {
    private enum class PendingLobbyAction {
        CREATE,
        JOIN,
    }

    private val _state =
        MutableStateFlow(
            LobbyUiState(
                serverUrl = config.defaultServerUrl,
                statusText = config.statusNotConnected,
            ),
        )
    val state: StateFlow<LobbyUiState> = _state.asStateFlow()

    /*
     * Die Lobby-Playerliste kommt als einzelne Join/Leave/Kick-Events. Eine Map
     * nach PlayerId verhindert Duplikate und erlaubt späteres Publizieren als
     * geordnete UI-Liste.
     */
    private val playersById = linkedMapOf<Long, LobbyPlayerUi>()
    private val commandDispatcher = LobbyCommandDispatcher(network::sendPayload)
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

    init {
        scope.launch {
            /*
             * Transportevents beschreiben nur Socket-Lifecycle. Fachliche
             * Entscheidungen wie "Reconnect starten" oder "Pending Create senden"
             * liegen bewusst hier im Controller.
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
             * ClientNetwork speichert nur den ersten technischen Session-Token.
             * Während eines Reconnects darf ein provisorischer neuer Token die
             * alte fachliche Session nicht ersetzen.
             */
            network.sessionToken.collect { sessionToken ->
                _state.update { it.copy(sessionToken = sessionToken?.value) }
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
    }

    fun updateServerUrl(serverUrl: String) {
        _state.update { it.copy(serverUrl = serverUrl) }
    }

    fun updatePlayerName(playerName: String) {
        _state.update { it.copy(playerName = playerName) }
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
        network.close()
    }

    fun leaveLobby() {
        val lobbyCode = state.value.activeLobbyCode ?: return
        scope.launch {
            runCatching {
                sendCommand(
                    LobbyCommand(
                        key = LobbyCommandKey.LEAVE_LOBBY,
                        payload =
                            LeaveLobbyRequest(
                                lobbyCode = parseLobbyCode(lobbyCode),
                            ),
                    ),
                )
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
                gameState = GameUiState(),
                pendingCommandKeys = emptySet(),
            )
        }
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
    ): Result<Unit> {
        if (state.value.pendingCommandKeys.contains(command.key)) {
            return Result.success(Unit)
        }

        _state.update {
            it.copy(pendingCommandKeys = it.pendingCommandKeys + command.key)
        }
        val result =
            runCatching {
                commandDispatcher.send(command)
            }
        if (!keepPendingUntilResponse || result.isFailure) {
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
        if (state.value.sessionToken == null) {
            _state.update { it.copy(sessionToken = payload.sessionToken.value) }
        }
    }

    private fun handleReconnectResponse(payload: ReconnectResponse) {
        awaitingReconnectResponse = false
        reconnectSessionToken = null

        if (!payload.success) {
            _state.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false,
                    isReconnecting = false,
                    statusText = config.statusReconnectFailed,
                    errorText = payload.errorCode?.name ?: config.errorReconnectFailed,
                )
            }
            clearPendingLobbyAction()
            return
        }

        _state.update {
            it.copy(
                isConnected = true,
                isConnecting = false,
                isReconnecting = false,
                statusText = config.statusConnected,
                errorText = null,
                activeLobbyCode = payload.lobbyCode?.value ?: it.activeLobbyCode,
                lobbyCode = payload.lobbyCode?.value ?: it.lobbyCode,
                playerName = payload.playerDisplayName ?: it.playerName,
                ownPlayerId = payload.playerId ?: it.ownPlayerId,
                gameState =
                    it.gameState.copy(
                        isCatchingUp = it.gameStarted,
                        isDesynced = false,
                        lastSyncError = null,
                    ),
            )
        }

        if (state.value.gameStarted) {
            requestGameCatchUp(
                reason = GameStateCatchUpReason.AFTER_RECONNECT,
                syncMessage =
                    "Session wiederhergestellt. " +
                        "Spielstand wird synchronisiert.",
            )
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
                clearPendingCommand(LobbyCommandKey.JOIN_LOBBY)
                handleJoinLobbyResponse(payload)
            }
            is JoinLobbyErrorResponse -> {
                clearPendingCommand(LobbyCommandKey.JOIN_LOBBY)
                pendingJoinCallback = null
                _state.update { it.copy(errorText = payload.reason) }
            }
            is PlayerJoinedLobbyEvent -> handlePlayerJoined(payload)
            is PlayerLeftLobbyEvent -> {
                playersById.remove(payload.playerId.value)
                publishPlayers()
            }
            is PlayerKickedLobbyEvent -> {
                playersById.remove(payload.targetPlayerId.value)
                publishPlayers()
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
            is GameStateDeltaEvent -> handleGameStateDelta(payload)
            is PhaseBoundaryEvent ->
                applyGameState { current, _ ->
                    ClientGameStateReducer.applyPhaseBoundary(current, payload)
                }
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
        }
    }

    private fun handlePlayerJoined(payload: PlayerJoinedLobbyEvent) {
        playersById[payload.playerId.value] =
            LobbyPlayerUi(
                playerId = payload.playerId,
                displayName = payload.playerDisplayName,
                isHost = payload.isHost,
            )

        _state.update { current ->
            val ownPlayerId =
                current.ownPlayerId
                    ?: payload.playerId.takeIf { payload.playerDisplayName == current.playerName }
            current.copy(ownPlayerId = ownPlayerId)
        }
        publishPlayers()
    }

    private fun handleStartGameResponse(payload: StartGameResponse) {
        if (payload.success) {
            _state.update { it.copy(errorText = null) }
        }
    }

    private fun handleGameStarted(payload: GameStartedEvent) {
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
        val result =
            ClientGameStateReducer.applyDelta(
                current = state.value.gameState,
                delta = payload,
                players = state.value.players,
            )

        _state.update { it.copy(gameState = result.state) }
        if (result.needsCatchUp) {
            requestGameCatchUp(
                reason = GameStateCatchUpReason.MISSING_DELTA,
                syncMessage = result.state.lastSyncError,
            )
        }
    }

    private fun applyGameState(
        reducer: (current: GameUiState, players: List<LobbyPlayerUi>) -> GameUiState,
    ) {
        _state.update { current ->
            current.copy(gameState = reducer(current.gameState, current.players))
        }
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
                                playerDisplayName = snapshot.playerName,
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
                                playerDisplayName = state.value.playerName,
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
        _state.update {
            it.copy(
                players = emptyList(),
                playerNames = emptyList(),
                ownPlayerId = null,
                gameStarted = false,
                gameState = GameUiState(),
                pendingCommandKeys = emptySet(),
            )
        }
    }

    private fun clearPendingLobbyAction() {
        pendingLobbyAction = null
        pendingCreateCallback = null
        pendingJoinCallback = null
        _state.update { it.copy(pendingCommandKeys = emptySet()) }
    }

    private fun publishPlayers() {
        val players = playersById.values.toList()
        _state.update {
            it.copy(
                players = players,
                playerNames = players.map(LobbyPlayerUi::displayName),
            )
        }
    }

    private fun ensureOwnPlayerName(
        currentNames: List<String>,
        ownName: String,
    ): List<String> {
        if (ownName.isBlank()) {
            return currentNames
        }
        if (currentNames.contains(ownName)) {
            return currentNames
        }
        return currentNames + ownName
    }
}
