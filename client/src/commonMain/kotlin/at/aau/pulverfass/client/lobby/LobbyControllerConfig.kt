package at.aau.pulverfass.client.lobby

/**
 * Konfigurations- und Textsammlung für den aktuellen [LobbyController].
 *
 * Die Klasse bündelt bewusst sowohl technische Defaults als auch UI-nahe
 * Status- und Fehlermeldungen für den derzeit kleinen App-Scope. Für größere
 * Features wäre eine spätere Trennung in Infrastruktur- und UI-Konfiguration
 * sinnvoll, aktuell existiert aber nur ein gemeinsamer Konfigurationspunkt.
 */
data class LobbyControllerConfig(
    val defaultServerUrl: String = "ws://5.189.160.80:8080/ws",
    val disconnectReason: String = "Client disconnected",
    val lobbyCodeLength: Int = 4,
    val reconnectMaxAttempts: Int = 5,
    val reconnectRetryDelayMillis: Long = 750L,
    val statusNotConnected: String = "Nicht verbunden",
    val statusConnecting: String = "Verbinde...",
    val statusReconnecting: String = "Verbinde erneut...",
    val statusConnected: String = "Verbunden",
    val statusDisconnected: String = "Getrennt",
    val statusConnectionError: String = "Verbindungsfehler",
    val statusConnectionFailed: String = "Verbindung fehlgeschlagen",
    val statusReconnectFailed: String = "Reconnect fehlgeschlagen",
    val errorPlayerNameRequired: String = "Bitte zuerst einen Spielernamen eingeben",
    val errorConnectFirst: String = "Bitte zuerst mit dem Server verbinden",
    val errorTransportUnknown: String = "Unbekannter Transportfehler",
    val errorPacketDecode: String = "Paket konnte nicht dekodiert werden",
    val errorCreateFailed: String = "Create request fehlgeschlagen",
    val errorJoinFailed: String = "Join request fehlgeschlagen",
    val errorLobbyCodeLength: String = "Lobbycode muss 4-stellig sein",
    val errorLobbyMissing: String = "Keine aktive Lobby gefunden",
    val errorStartGameFailed: String = "Spielstart fehlgeschlagen",
    val errorCatchUpFailed: String = "Spielstand konnte nicht synchronisiert werden",
    val errorMapGetFailed: String = "Kartenzustand konnte nicht geladen werden",
    val errorTurnStateGetFailed: String = "Zugstatus konnte nicht geladen werden",
    val errorPlayerIdMissing: String = "Eigene Spieler-ID fehlt noch",
    val errorTurnAdvanceNotAllowed: String = "Du kannst die aktuelle Phase nicht beenden",
    val errorTurnAdvanceFailed: String = "Phasenwechsel fehlgeschlagen",
    val errorReinforcementsNotAllowed: String =
        "Verstärkungen können gerade nicht platziert werden",
    val errorReinforcementTargetMissing: String = "Wähle zuerst ein eigenes Gebiet aus",
    val errorPlaceReinforcementsFailed: String = "Verstärkungen konnten nicht platziert werden",
    val errorConfirmReinforcementsFailed: String = "Verstärkungsphase konnte nicht beendet werden",
    val errorTradeInNotAllowed: String = "Wähle genau drei Karten zum Eintauschen aus",
    val errorTradeInFailed: String = "Kartentausch fehlgeschlagen",
    val errorAttackNotAllowed: String = "Ein Angriff ist gerade nicht möglich",
    val errorAttackSelectionMissing: String =
        "Wähle ein eigenes und ein benachbartes fremdes Gebiet",
    val errorAttackFailed: String = "Angriff fehlgeschlagen",
    val autoAttackStarted: String = "Auto-Angriff gestartet.",
    val autoAttackPending: String = "Auto-Angriff wartet auf das Serverergebnis.",
    val autoAttackStoppedCaptured: String =
        "Zielgebiet erobert. Auto-Angriff bleibt aktiviert.",
    val autoAttackStoppedSourceWeak: String =
        "Ausgangsgebiet hat nicht mehr genug Truppen für die gewählte Angriffsstärke.",
    val autoAttackStoppedPhaseChanged: String =
        "Auto-Angriff beendet, weil die Angriffsphase nicht mehr aktiv ist.",
    val autoAttackStoppedActivePlayerChanged: String =
        "Auto-Angriff beendet, weil ein anderer Spieler am Zug ist.",
    val autoAttackStoppedInvalidTarget: String =
        "Auto-Angriff beendet, weil das Ziel nicht mehr angreifbar ist.",
    val autoAttackStoppedConnectionLost: String =
        "Auto-Angriff beendet, weil die Verbindung unterbrochen wurde.",
    val autoAttackStoppedRejected: String = "Auto-Angriff durch Serverfehler beendet.",
    val errorConfirmAttackFailed: String = "Angriffsphase konnte nicht beendet werden",
    val errorFortifyNotAllowed: String = "Truppen können gerade nicht verschoben werden",
    val errorFortifySelectionMissing: String =
        "Wähle zwei verbundene eigene Gebiete für die Verschiebung",
    val errorFortifyFailed: String = "Truppenverschiebung fehlgeschlagen",
    val errorDisconnectedDuringGame: String =
        "Verbindung getrennt. Reconnect wird vorbereitet.",
    val errorReconnectFailed: String = "Session konnte nicht wiederhergestellt werden",
    val errorReconnectTokenMissing: String = "Session-Token für Reconnect fehlt",
    val errorUnknown: String = "Unbekannter Fehler",
) {
    init {
        require(reconnectMaxAttempts > 0) {
            "reconnectMaxAttempts muss positiv sein."
        }
        require(reconnectRetryDelayMillis >= 0L) {
            "reconnectRetryDelayMillis darf nicht negativ sein."
        }
    }
}
