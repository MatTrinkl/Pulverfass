package at.aau.pulverfass.app.lobby

/**
 * Konfigurations- und Textsammlung für den aktuellen [LobbyController].
 *
 * Die Klasse bündelt bewusst sowohl technische Defaults als auch UI-nahe
 * Status- und Fehlermeldungen für den derzeit kleinen App-Scope. Für größere
 * Features wäre eine spätere Trennung in Infrastruktur- und UI-Konfiguration
 * sinnvoll, aktuell existiert aber nur ein gemeinsamer Konfigurationspunkt.
 */
data class LobbyControllerConfig(
    val defaultServerUrl: String = "ws://10.0.2.2:8080/ws",
    val disconnectReason: String = "Client disconnected",
    val lobbyCodeLength: Int = 4,
    val statusNotConnected: String = "Nicht verbunden",
    val statusConnecting: String = "Verbinde...",
    val statusConnected: String = "Verbunden",
    val statusDisconnected: String = "Getrennt",
    val statusConnectionError: String = "Verbindungsfehler",
    val statusConnectionFailed: String = "Verbindung fehlgeschlagen",
    val errorPlayerNameRequired: String = "Bitte zuerst einen Spielernamen eingeben",
    val errorConnectFirst: String = "Bitte zuerst mit dem Server verbinden",
    val errorTransportUnknown: String = "Unbekannter Transportfehler",
    val errorPacketDecode: String = "Paket konnte nicht dekodiert werden",
    val errorCreateFailed: String = "Create request fehlgeschlagen",
    val errorJoinFailed: String = "Join request fehlgeschlagen",
    val errorLobbyCodeLength: String = "Lobbycode muss 4-stellig sein",
    val errorUnknown: String = "Unbekannter Fehler",
)
