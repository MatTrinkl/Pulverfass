package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * UI-Projektion des aktuell im Android-Client genutzten Lobby-Zustands.
 *
 * Das Modell bündelt den Lobby-Flow und den daraus gestarteten GameState, damit
 * Android dieselbe WebSocket-Verbindung für Lobby und Spiel wiederverwendet.
 */
data class LobbyUiState(
    val serverUrl: String = "ws://10.0.2.2:8080/ws",
    val playerName: String = "",
    val lobbyCode: String = "",
    val activeLobbyCode: String? = null,
    val isJoining: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isHost: Boolean = false,
    val statusText: String = "Nicht verbunden",
    val errorText: String? = null,
    val lastMessageType: String? = null,
    val ownPlayerId: PlayerId? = null,
    val players: List<LobbyPlayerUi> = emptyList(),
    val playerNames: List<String> = emptyList(),
    val gameStarted: Boolean = false,
    val gameState: GameUiState = GameUiState(),
)
