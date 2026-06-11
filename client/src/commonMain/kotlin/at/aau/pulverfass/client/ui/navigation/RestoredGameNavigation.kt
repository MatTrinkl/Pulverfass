package at.aau.pulverfass.client.ui.navigation

import at.aau.pulverfass.client.lobby.LobbyUiState

/**
 * Ermittelt, ob die App nach einem wiederhergestellten Spielkontext automatisch
 * in den Game-Ladepfad zurückkehren soll.
 *
 * Der Controller bleibt die fachliche Quelle der Wahrheit: Diese Funktion baut
 * keinen Backend-Zustand nach, sondern prüft nur, ob der vom Server bestätigte
 * Reconnect bereits genug Kontext geliefert hat. Ohne Lobby, eigenen Spieler
 * und laufendes Spiel bleibt die UI auf dem aktuellen Einstiegspunkt.
 */
internal fun restoredGameNavigationTarget(lobbyState: LobbyUiState): String? {
    val hasRestoredRunningGame =
        lobbyState.isConnected &&
            !lobbyState.isConnecting &&
            !lobbyState.isReconnecting &&
            lobbyState.sessionToken != null &&
            lobbyState.activeLobbyCode != null &&
            lobbyState.ownPlayerId != null &&
            lobbyState.gameStarted

    return if (hasRestoredRunningGame) {
        Screen.LoadGame.route
    } else {
        null
    }
}

/**
 * Beschränkt die automatische Rücknavigation auf den Lobby-Einstieg.
 *
 * Dadurch kann ein normaler Spielstart aus dem Warteraum weiterhin vom
 * Warteraum selbst navigiert werden. Der Reconnect-Fall läuft nach einem
 * App-Neustart zuerst durch den normalen Asset-Load und landet dann im
 * Lobby-Screen, von wo er sauber zurück in den Game-Flow geführt wird.
 */
internal fun canAutoNavigateToRestoredGame(currentRoute: String?): Boolean =
    currentRoute == Screen.Lobby.route
