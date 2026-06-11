package at.aau.pulverfass.client.storage

import android.content.Context
import at.aau.pulverfass.client.AppContextHolder

/**
 * Persistiert Spielernamen und Charakter-ID im privaten App-Speicher.
 *
 * Diese Werte sind lokale Einstellungen und kein autoritativer Lobby-Zustand.
 * Der Server erhält sie weiterhin erst über Join- und CharacterSelect-Requests.
 *
 * @param context Android-Kontext für den privaten App-Speicher
 */
class SharedPreferencesPlayerNameStore(
    context: Context,
) : PlayerNameStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun readPlayerName(): String? =
        preferences.getString(KEY_PLAYER_NAME, null)
            ?.takeIf { it.isNotBlank() }

    override fun savePlayerName(playerName: String) {
        preferences.edit()
            .putString(KEY_PLAYER_NAME, playerName)
            .apply()
    }

    override fun readCharacterId(): String? =
        preferences.getString(KEY_CHARACTER_ID, null)
            ?.takeIf { it.isNotBlank() }

    override fun saveCharacterId(characterId: String) {
        preferences.edit()
            .putString(KEY_CHARACTER_ID, characterId)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "pulverfass_player_settings"
        const val KEY_PLAYER_NAME = "player_name"
        const val KEY_CHARACTER_ID = "character_id"
    }
}

/**
 * Kleine Android-Persistenz für Reconnect-Metadaten.
 *
 * Eine Datenbank wäre für einen einzelnen Token und wenige Begleitwerte
 * unnötig schwer. `SharedPreferences` reicht hier, weil der Zugriff synchron,
 * klein und nur beim App-Start beziehungsweise bei klaren Zustandswechseln
 * passiert.
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

actual fun createPlayerNameStore(): PlayerNameStore =
    SharedPreferencesPlayerNameStore(
        AppContextHolder.context,
    )

actual fun createReconnectSessionStore(): ReconnectSessionStore =
    SharedPreferencesReconnectSessionStore(
        AppContextHolder.context,
    )
