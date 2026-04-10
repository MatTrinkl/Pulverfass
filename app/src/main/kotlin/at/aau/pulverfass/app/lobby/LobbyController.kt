package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.app.network.ClientNetwork
import at.aau.pulverfass.shared.network.message.MessageType
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
import kotlin.random.Random

/**
 * UI-zentrierte Lobby-Schicht für die Android-App.
 *
 * Der Controller verbindet den LobbyScreen mit der neuen technischen
 * WebSocket-Pipeline und kapselt Statusverwaltung, Fehlerbehandlung und
 * technische Requests.
 */
class LobbyController(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val network: ClientNetwork = ClientNetwork(scope),
) {
    private val _state = MutableStateFlow(LobbyUiState())
    val state: StateFlow<LobbyUiState> = _state.asStateFlow()

    init {
        scope.launch {
            network.transport.events.collect { event ->
                when (event) {
                    is Connected -> {
                        _state.update {
                            it.copy(
                                isConnected = true,
                                isConnecting = false,
                                statusText = "Verbunden",
                                errorText = null,
                            )
                        }
                    }

                    is Disconnected -> {
                        _state.update {
                            it.copy(
                                isConnected = false,
                                isConnecting = false,
                                statusText = "Getrennt",
                            )
                        }
                    }

                    is TransportError -> {
                        _state.update {
                            it.copy(
                                isConnected = false,
                                isConnecting = false,
                                statusText = "Verbindungsfehler",
                                errorText =
                                    event.cause.message
                                        ?: "Unbekannter Transportfehler",
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }

        scope.launch {
            network.packetReceiver.packets.collect { packet ->
                _state.update {
                    it.copy(
                        lastMessageType = packet.header.type.name,
                    )
                }
            }
        }

        scope.launch {
            network.packetReceiver.errors.collect { error ->
                _state.update {
                    it.copy(
                        errorText =
                            error.message
                                ?: "Paket konnte nicht dekodiert werden",
                    )
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
        _state.update { it.copy(lobbyCode = lobbyCode) }
    }

    fun setJoining(isJoining: Boolean) {
        _state.update { it.copy(isJoining = isJoining) }
    }

    fun connect() {
        val snapshot = state.value
        if (snapshot.playerName.isBlank()) {
            _state.update { it.copy(errorText = "Bitte zuerst einen Spielernamen eingeben") }
            return
        }

        scope.launch {
            _state.update {
                it.copy(
                    isConnecting = true,
                    statusText = "Verbinde...",
                    errorText = null,
                )
            }

            runCatching {
                network.connect(snapshot.serverUrl)
                network.sendLoginRequest(
                    username = snapshot.playerName,
                    password = "android-template",
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        statusText = "Verbindung fehlgeschlagen",
                        errorText = error.message ?: "Unbekannter Fehler",
                    )
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            runCatching { network.disconnect("Client disconnected") }
        }
    }

    /**
     * Sendet einen technischen Create-Request und liefert einen lokalen Lobbycode.
     *
     * Die Payload-Struktur ist absichtlich minimal gehalten, bis konkrete
     * Shared-Payload-Klassen für GAME_CREATE_REQUEST vorliegen.
     */
    fun createLobby(onLobbyReady: (String) -> Unit) {
        val snapshot = state.value
        if (!snapshot.isConnected) {
            _state.update { it.copy(errorText = "Bitte zuerst mit dem Server verbinden") }
            return
        }

        val generatedCode = Random.nextInt(1000, 10000).toString()

        scope.launch {
            runCatching {
                network.sendJsonMessage(
                    messageType = MessageType.GAME_CREATE_REQUEST,
                    payloadJson =
                        """
                        {"playerName":"${snapshot.playerName}","clientLobbyCode":"$generatedCode"}
                        """.trimIndent(),
                )
            }.onSuccess {
                onLobbyReady(generatedCode)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        errorText = error.message ?: "Create request fehlgeschlagen",
                    )
                }
            }
        }
    }

    /**
     * Sendet einen technischen Join-Request für den angegebenen Lobbycode.
     *
     * Die Payload-Struktur ist absichtlich minimal gehalten, bis konkrete
     * Shared-Payload-Klassen für GAME_JOIN_REQUEST vorliegen.
     */
    fun joinLobby(onLobbyReady: (String) -> Unit) {
        val snapshot = state.value
        if (!snapshot.isConnected) {
            _state.update { it.copy(errorText = "Bitte zuerst mit dem Server verbinden") }
            return
        }
        if (snapshot.lobbyCode.length != 4) {
            _state.update { it.copy(errorText = "Lobbycode muss 4-stellig sein") }
            return
        }

        scope.launch {
            runCatching {
                network.sendJsonMessage(
                    messageType = MessageType.GAME_JOIN_REQUEST,
                    payloadJson =
                        """
                        {"playerName":"${snapshot.playerName}","lobbyCode":"${snapshot.lobbyCode}"}
                        """.trimIndent(),
                )
            }.onSuccess {
                onLobbyReady(snapshot.lobbyCode)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        errorText = error.message ?: "Join request fehlgeschlagen",
                    )
                }
            }
        }
    }

    fun close() {
        network.close()
    }
}

data class LobbyUiState(
    val serverUrl: String = "ws://10.0.2.2:8080/ws",
    val playerName: String = "",
    val lobbyCode: String = "",
    val isJoining: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val statusText: String = "Nicht verbunden",
    val errorText: String? = null,
    val lastMessageType: String? = null,
)
