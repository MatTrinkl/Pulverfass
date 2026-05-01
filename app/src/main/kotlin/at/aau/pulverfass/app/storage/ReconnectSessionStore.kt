package at.aau.pulverfass.app.storage

import android.content.Context

/**
 * Speichert genau die lokale Basis, die der Android-Client für einen echten
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
 * No-Op-Store für Tests und Call-Sites ohne Android-Persistenz.
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

/**
 * Kleine Android-Persistenz für Reconnect-Metadaten.
 *
 * Eine Datenbank wäre für einen einzelnen Token und wenige Begleitwerte
 * unnötig schwer. `SharedPreferences` reicht hier, weil der Zugriff synchron,
 * klein und nur beim App-Start beziehungsweise bei klaren Zustandswechseln
 * passiert. Verschlüsselung kann später ergänzt werden, falls das Projekt eine
 * Security-Bibliothek dafür einführt.
 *
 * @param context Android-Kontext für den privaten App-Speicher
 */
class SharedPreferencesReconnectSessionStore(
    context: Context,
) : ReconnectSessionStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun readSessionToken(): String? =
        preferences.getString(KEY_SESSION_TOKEN, null)
            ?.takeIf { it.isNotBlank() }

    override fun saveSessionToken(sessionToken: String) {
        preferences.edit()
            .putString(KEY_SESSION_TOKEN, sessionToken)
            .apply()
    }

    override fun clearSessionToken() {
        preferences.edit()
            .remove(KEY_SESSION_TOKEN)
            .apply()
    }

    override fun readServerUrl(): String? =
        preferences.getString(KEY_SERVER_URL, null)
            ?.takeIf { it.isNotBlank() }

    override fun saveServerUrl(serverUrl: String) {
        preferences.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .apply()
    }

    override fun readWasGameStarted(): Boolean = preferences.getBoolean(KEY_WAS_GAME_STARTED, false)

    override fun saveWasGameStarted(wasGameStarted: Boolean) {
        preferences.edit()
            .putBoolean(KEY_WAS_GAME_STARTED, wasGameStarted)
            .apply()
    }

    override fun clearSession() {
        preferences.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_WAS_GAME_STARTED)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "pulverfass_reconnect_session"
        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_WAS_GAME_STARTED = "was_game_started"
    }
}
