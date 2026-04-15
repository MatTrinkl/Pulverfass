package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.app.network.ClientNetwork
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
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

    private val playersById = linkedMapOf<Long, String>()
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
            )
        }
        playersById.clear()
    }

    private fun handlePayload(payload: Any) {
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
            is PlayerJoinedLobbyEvent -> {
                playersById[payload.playerId.value] = payload.playerDisplayName
                publishPlayerNames()
            }
            is PlayerLeftLobbyEvent -> {
                playersById.remove(payload.playerId.value)
                publishPlayerNames()
            }
            is PlayerKickedLobbyEvent -> {
                playersById.remove(payload.targetPlayerId.value)
                publishPlayerNames()
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

    private fun parseLobbyCode(value: String) =
        at.aau.pulverfass.shared.ids.LobbyCode(value.uppercase())

    private fun resetLobbyMembers() {
        playersById.clear()
        publishPlayerNames()
    }

    private fun clearPendingLobbyAction() {
        pendingLobbyAction = null
        pendingCreateCallback = null
        pendingJoinCallback = null
    }

    private fun publishPlayerNames() {
        _state.update { it.copy(playerNames = playersById.values.toList()) }
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
