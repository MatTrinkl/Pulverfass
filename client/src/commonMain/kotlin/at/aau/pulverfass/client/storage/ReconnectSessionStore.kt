package at.aau.pulverfass.client.storage

/**
 * Speichert genau die lokale Basis, die der Client für einen echten
 * Reconnect nach Prozessende braucht.
 *
 * Der Server bleibt die Autorität für Spieler, Lobby und Spielstand. Lokal
 * wird deshalb kein GameState gespiegelt, sondern nur der technische Schlüssel
 * zur alten Session und die Zieladresse des zuletzt genutzten Servers. Das
 * Flag [readWasGameStarted] ist kein autoritativer Spielzustand, sondern nur
 * ein Hinweis für den Client, ob nach einem erfolgreichen Reconnect sofort ein
 * Catch-up-Snapshot angefordert werden soll.
 */
interface ReconnectSessionStore {
    /**
     * Liefert den zuletzt bestätigten Session-Token oder `null`.
     */
    fun readSessionToken(): String?

    /**
     * Speichert den Session-Token, den der Server für spätere Reconnects
     * ausgegeben hat.
     *
     * @param sessionToken stabiler Token der fachlichen Server-Session
     */
    fun saveSessionToken(sessionToken: String)

    /**
     * Entfernt den gespeicherten Session-Token.
     *
     * Diese Methode wird bewusst nicht bei einem normalen Verbindungsverlust
     * aufgerufen. Der Token darf erst verschwinden, wenn der Nutzer die Lobby
     * aktiv verlässt oder der Server den Reconnect-Token ablehnt.
     */
    fun clearSessionToken()

    /**
     * Liefert die zuletzt verwendete WebSocket-URL oder `null`.
     */
    fun readServerUrl(): String?

    /**
     * Speichert die letzte Server-Adresse, damit ein Auto-Reconnect nach
     * App-Neustart nicht versehentlich gegen die Default-URL läuft.
     *
     * @param serverUrl WebSocket-URL des zuletzt ausgewählten Servers
     */
    fun saveServerUrl(serverUrl: String)

    /**
     * Liefert, ob der Client zuletzt bereits in einer gestarteten Partie war.
     */
    fun readWasGameStarted(): Boolean

    /**
     * Speichert einen rein lokalen Hinweis auf den zuletzt bekannten
     * Spielstart-Zustand.
     *
     * @param wasGameStarted `true`, wenn der Client zuletzt ein gestartetes
     * Spiel gesehen hat
     */
    fun saveWasGameStarted(wasGameStarted: Boolean)

    /**
     * Löscht alle Werte, die eine alte fachliche Session wiederherstellen
     * würden.
     */
    fun clearSession()
}

/**
 * No-Op-Store für Tests und Call-Sites ohne Plattform-Persistenz.
 */
object NoOpReconnectSessionStore : ReconnectSessionStore {
    override fun readSessionToken(): String? = null

    override fun saveSessionToken(sessionToken: String) = Unit

    override fun clearSessionToken() = Unit

    override fun readServerUrl(): String? = null

    override fun saveServerUrl(serverUrl: String) = Unit

    override fun readWasGameStarted(): Boolean = false

    override fun saveWasGameStarted(wasGameStarted: Boolean) = Unit

    override fun clearSession() = Unit
}
