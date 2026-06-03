package at.aau.pulverfass.app.lobby

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * UI-Projektion des aktuell im Android-Client genutzten Lobby-Zustands.
 *
 * Das Modell bündelt den Lobby-Flow und den daraus gestarteten GameState, damit
 * Android dieselbe WebSocket-Verbindung für Lobby und Spiel wiederverwendet.
 */
data class LobbyUiState(
    val serverUrl: String = "ws://5.189.160.80:8080/ws",
    val playerName: String = "",
    val lobbyCode: String = "",
    val activeLobbyCode: String? = null,
    val isJoining: Boolean = false,
    val isConnecting: Boolean = false,
    val isReconnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isHost: Boolean = false,
    val statusText: String = "Nicht verbunden",
    val errorText: String? = null,
    val sessionToken: String? = null,
    val lastMessageType: String? = null,
    val ownPlayerId: PlayerId? = null,
    val players: List<LobbyPlayerUi> = emptyList(),
    val playerNames: List<String> = emptyList(),
    val gameStarted: Boolean = false,
    val gameState: GameUiState = GameUiState(),
    val pendingCommandKeys: Set<LobbyCommandKey> = emptySet(),
    val onlinePlayerCount: Int? = null,
    val globalPlayerCount: Int? = null,
    val playerColor: Color? = null,
    val characterId: String? = null,
    val characterSelectError: String? = null,
)
