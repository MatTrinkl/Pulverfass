package at.aau.pulverfass.client.storage

/**
 * Lokale Benutzereinstellungen für Name und Charakter.
 *
 * Anzeigename und Charakter-ID sind unabhängig von einer laufenden Lobby- oder
 * Reconnect-Session. Dadurch kann er nach einem App-Neustart bereits im
 * Options- und Lobby-Screen wieder angezeigt werden.
 */
interface PlayerNameStore {
    /**
     * Liefert den gespeicherten Spielernamen oder `null`, falls kein Name
     * ausgewählt wurde.
     */
    fun readPlayerName(): String?

    /**
     * Speichert den aktuell im UI eingegebenen Spielernamen.
     *
     * @param playerName Anzeigename, der für den nächsten Lobby-Join verwendet wird
     */
    fun savePlayerName(playerName: String)

    /**
     * Liefert die zuletzt gewählte Charakter-ID oder `null`.
     */
    fun readCharacterId(): String?

    /**
     * Speichert die gewählte Charakter-ID.
     *
     * @param characterId stabile ID aus `Characters`.
     */
    fun saveCharacterId(characterId: String)

    /**
     * Liefert, ob Auto-Angriff als globale Voreinstellung aktiviert ist.
     */
    fun readAutoAttackEnabled(): Boolean

    /**
     * Speichert die globale Auto-Angriff-Voreinstellung.
     *
     * @param enabled `true`, wenn neue Angriffsauswahlen den Auto-Angriff
     * voraktiviert anzeigen sollen.
     */
    fun saveAutoAttackEnabled(enabled: Boolean)
}

/**
 * No-Op-Implementierung für Tests oder Controller-Instanzen ohne Plattform-Store.
 */
object NoOpPlayerNameStore : PlayerNameStore {
    override fun readPlayerName(): String? = null

    override fun savePlayerName(playerName: String) = Unit

    override fun readCharacterId(): String? = null

    override fun saveCharacterId(characterId: String) = Unit

    override fun readAutoAttackEnabled(): Boolean = false

    override fun saveAutoAttackEnabled(enabled: Boolean) = Unit
}
