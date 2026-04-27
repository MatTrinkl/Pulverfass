package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.app.game.ClientGameStateReducer
import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.app.network.ClientNetwork
import at.aau.pulverfass.shared.ids.LobbyCode
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
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
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
import kotlinx.coroutines.SupervisorJob
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

    private val playersById = linkedMapOf<Long, LobbyPlayerUi>()
    private var pendingCreateCallback: ((String) -> Unit)? = null
    private var pendingJoinCallback: ((String) -> Unit)? = null
    private var pendingLobbyAction: PendingLobbyAction? = null

    init {
        scope.launch {
            network.transport.events.collect { event ->
                when (event) {
                    is Connected -> {
                        _state.update {
                            it.copy(
                                isConnected = true,
                                isConnecting = false,
                                statusText = config.statusConnected,
                                errorText = null,
                            )
                        }
                        executePendingLobbyActionIfAny()
                    }

                    is Disconnected -> {
                        _state.update {
                            it.copy(
                                isConnected = false,
                                isConnecting = false,
                                statusText = config.statusDisconnected,
                            )
                        }
                        clearPendingLobbyAction()
                    }

                    is TransportError -> {
                        _state.update {
                            it.copy(
                                isConnected = false,
                                isConnecting = false,
                                statusText = config.statusConnectionError,
                                errorText = event.cause.message ?: config.errorTransportUnknown,
                            )
                        }
                        clearPendingLobbyAction()
                    }

                    else -> Unit
                }
            }
        }

        scope.launch {
            network.packetReceiver.packets.collect { packet ->
                _state.update { it.copy(lastMessageType = packet.header.type.name) }

                runCatching {
                    MessageCodec.decodePayload(packet)
                }.onSuccess { payload ->
                    handlePayload(payload)
                }.onFailure { error ->
                    _state.update { it.copy(errorText = error.message ?: config.errorPacketDecode) }
                }
            }
        }

        scope.launch {
            network.packetReceiver.errors.collect { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorPacketDecode)
                }
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
        val snapshot = state.value
        if (snapshot.playerName.isBlank()) {
            _state.update { it.copy(errorText = config.errorPlayerNameRequired) }
            return
        }
        if (snapshot.isConnected || snapshot.isConnecting) {
            return
        }

        scope.launch {
            _state.update {
                it.copy(
                    isConnecting = true,
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
                        statusText = config.statusConnectionFailed,
                        errorText = error.message ?: config.errorUnknown,
                    )
                }
                clearPendingLobbyAction()
            }
        }
    }

    fun disconnect() {
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
        network.close()
    }

    fun leaveLobby() {
        val lobbyCode = state.value.activeLobbyCode ?: return
        scope.launch {
            runCatching {
                network.sendPayload(
                    LeaveLobbyRequest(
                        lobbyCode = parseLobbyCode(lobbyCode),
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
            runCatching {
                network.sendPayload(StartGameRequest(lobbyCode = parseLobbyCode(lobbyCode)))
            }.onFailure { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorStartGameFailed)
                }
            }
        }
    }

    fun requestGameCatchUp(
        reason: GameStateCatchUpReason = GameStateCatchUpReason.AFTER_RECONNECT,
    ) {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode ?: return

        _state.update {
            it.copy(gameState = it.gameState.copy(isCatchingUp = true, lastSyncError = null))
        }
        scope.launch {
            runCatching {
                network.sendPayload(
                    GameStateCatchUpRequest(
                        lobbyCode = parseLobbyCode(lobbyCode),
                        clientStateVersion = snapshot.gameState.stateVersion,
                        reason = reason,
                    ),
                )
            }.onFailure { error ->
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

    fun requestPrivateGameState() {
        val snapshot = state.value
        val lobbyCode = snapshot.activeLobbyCode ?: return
        val playerId = snapshot.ownPlayerId ?: return

        scope.launch {
            runCatching {
                network.sendPayload(
                    GameStatePrivateGetRequest(
                        lobbyCode = parseLobbyCode(lobbyCode),
                        playerId = playerId,
                    ),
                )
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
        if (!snapshot.gameState.canRequestTurnAdvance(playerId)) {
            _state.update { it.copy(errorText = config.errorTurnAdvanceNotAllowed) }
            return
        }

        scope.launch {
            runCatching {
                network.sendPayload(
                    TurnAdvanceRequest(
                        lobbyCode = parseLobbyCode(lobbyCode),
                        playerId = playerId,
                        expectedPhase = snapshot.gameState.turnPhase,
                    ),
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(errorText = error.message ?: config.errorTurnAdvanceFailed)
                }
            }
        }
    }

    fun selectGameRegion(regionId: String) {
        _state.update {
            it.copy(gameState = ClientGameStateReducer.selectRegion(it.gameState, regionId))
        }
    }

    fun toggleCards() {
        _state.update {
            it.copy(gameState = ClientGameStateReducer.toggleCards(it.gameState))
        }
    }

    private fun handlePayload(payload: NetworkMessagePayload) {
        when (payload) {
            is CreateLobbyResponse -> handleCreateLobbyResponse(payload)
            is CreateLobbyErrorResponse -> {
                pendingCreateCallback = null
                _state.update { it.copy(errorText = payload.reason) }
            }
            is JoinLobbyResponse -> handleJoinLobbyResponse(payload)
            is JoinLobbyErrorResponse -> {
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
            is StartGameResponse -> handleStartGameResponse(payload)
            is StartGameErrorResponse -> updateGameError(payload.reason)
            is GameStartedEvent -> handleGameStarted(payload)
            is MapGetResponse ->
                applyGameState { current, players ->
                    ClientGameStateReducer.applyMapGetResponse(current, payload, players)
                }
            is MapGetErrorResponse -> updateGameError(payload.reason)
            is GameStateCatchUpResponse -> {
                applyGameState { current, players ->
                    ClientGameStateReducer.applyCatchUpResponse(current, payload, players)
                }
                requestPrivateGameState()
            }
            is GameStateCatchUpErrorResponse -> updateGameError(payload.reason)
            is GameStateSnapshotBroadcast ->
                applyGameState { current, players ->
                    ClientGameStateReducer.applySnapshotBroadcast(current, payload, players)
                }
            is GameStateDeltaEvent -> handleGameStateDelta(payload)
            is PhaseBoundaryEvent ->
                applyGameState { current, _ ->
                    ClientGameStateReducer.applyPhaseBoundary(current, payload)
                }
            is TurnStateGetResponse ->
                applyGameState { current, _ ->
                    ClientGameStateReducer.applyTurnStateGetResponse(current, payload)
                }
            is TurnAdvanceResponse -> _state.update { it.copy(errorText = null) }
            is TurnAdvanceErrorResponse -> updateGameError(payload.reason)
            is TurnStateGetErrorResponse -> updateGameError(payload.reason)
            is GameStatePrivateGetResponse ->
                applyGameState { current, _ ->
                    ClientGameStateReducer.applyPrivateGetResponse(current, payload)
                }
            is GameStatePrivateGetErrorResponse -> updateGameError(payload.reason)
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
            requestGameCatchUp(GameStateCatchUpReason.MISSING_DELTA)
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
            runCatching {
                network.sendPayload(CreateLobbyRequest)
            }.onFailure { error ->
                pendingCreateCallback = null
                _state.update {
                    it.copy(errorText = error.message ?: config.errorCreateFailed)
                }
            }
        }
    }

    private fun submitJoinLobbyRequest(snapshot: LobbyUiState = state.value) {
        scope.launch {
            runCatching {
                network.sendPayload(
                    JoinLobbyRequest(
                        lobbyCode = parseLobbyCode(snapshot.lobbyCode),
                        playerDisplayName = snapshot.playerName,
                    ),
                )
            }.onFailure { error ->
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
            runCatching {
                network.sendPayload(
                    JoinLobbyRequest(
                        lobbyCode = payload.lobbyCode,
                        playerDisplayName = state.value.playerName,
                    ),
                )
            }.onFailure { error ->
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
            )
        }
    }

    private fun clearPendingLobbyAction() {
        pendingLobbyAction = null
        pendingCreateCallback = null
        pendingJoinCallback = null
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
