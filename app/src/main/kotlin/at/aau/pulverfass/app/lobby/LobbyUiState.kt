package at.aau.pulverfass.app.lobby

/**
 * UI-Projektion des aktuell im Android-Client genutzten Lobby-Zustands.
 *
 * Das Modell deckt nur den heute produktiv implementierten Lobby-Flow ab.
 * Autoritativer Map-, Turn- oder sonstiger Gameplay-State wird hier bewusst
 * noch nicht abgebildet.
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
    val playerNames: List<String> = emptyList(),
)
