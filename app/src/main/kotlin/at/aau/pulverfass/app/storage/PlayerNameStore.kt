package at.aau.pulverfass.app.storage

import android.content.Context

/**
 * Lokale Benutzereinstellung für den zuletzt gewählten Spielernamen.
 *
 * Der Anzeigename ist unabhängig von einer laufenden Lobby- oder
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

    /** Liefert die zuletzt gewählte Charakter-ID oder `null`. */
    fun readCharacterId(): String?

    /** Speichert die gewählte Charakter-ID. */
    fun saveCharacterId(characterId: String)
}

/**
 * No-Op-Implementierung für Tests oder Controller-Instanzen ohne Android-Store.
 */
object NoOpPlayerNameStore : PlayerNameStore {
    override fun readPlayerName(): String? = null

    override fun savePlayerName(playerName: String) = Unit

    override fun readCharacterId(): String? = null

    override fun saveCharacterId(characterId: String) = Unit
}

/**
 * Persistiert den Spielernamen im privaten App-Speicher.
 *
 * Der Name ist eine kleine lokale Einstellung und kein autoritativer
 * Lobby-Zustand. Der Server erhält ihn weiterhin erst mit dem Join-Request.
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
