package at.aau.pulverfass.client.storage

import platform.Foundation.NSUserDefaults

/**
 * Persistiert Spielernamen und Charakter-ID in NSUserDefaults.
 *
 * Gegenstück zur SharedPreferences-Implementierung auf Android; die Keys sind
 * identisch benannt, damit die Semantik beider Plattformen deckungsgleich ist.
 */
internal class UserDefaultsPlayerNameStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : PlayerNameStore {
    override fun readPlayerName(): String? =
        defaults.stringForKey(KEY_PLAYER_NAME)
            ?.takeIf { it.isNotBlank() }

    override fun savePlayerName(playerName: String) {
        defaults.setObject(playerName, forKey = KEY_PLAYER_NAME)
    }

    override fun readCharacterId(): String? =
        defaults.stringForKey(KEY_CHARACTER_ID)
            ?.takeIf { it.isNotBlank() }

    override fun saveCharacterId(characterId: String) {
        defaults.setObject(characterId, forKey = KEY_CHARACTER_ID)
    }

    override fun readAutoAttackEnabled(): Boolean = defaults.boolForKey(KEY_AUTO_ATTACK_ENABLED)

    override fun saveAutoAttackEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = KEY_AUTO_ATTACK_ENABLED)
    }

    private companion object {
        const val KEY_PLAYER_NAME = "player_name"
        const val KEY_CHARACTER_ID = "character_id"
        const val KEY_AUTO_ATTACK_ENABLED = "auto_attack_enabled"
    }
}

/**
 * Kleine iOS-Persistenz für Reconnect-Metadaten via NSUserDefaults.
 */
internal class UserDefaultsReconnectSessionStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ReconnectSessionStore {
    override fun readSessionToken(): String? =
        defaults.stringForKey(KEY_SESSION_TOKEN)
            ?.takeIf { it.isNotBlank() }

    override fun saveSessionToken(sessionToken: String) {
        defaults.setObject(sessionToken, forKey = KEY_SESSION_TOKEN)
    }

    override fun clearSessionToken() {
        defaults.removeObjectForKey(KEY_SESSION_TOKEN)
    }

    override fun readServerUrl(): String? =
        defaults.stringForKey(KEY_SERVER_URL)
            ?.takeIf { it.isNotBlank() }

    override fun saveServerUrl(serverUrl: String) {
        defaults.setObject(serverUrl, forKey = KEY_SERVER_URL)
    }

    override fun readWasGameStarted(): Boolean = defaults.boolForKey(KEY_WAS_GAME_STARTED)

    override fun saveWasGameStarted(wasGameStarted: Boolean) {
        defaults.setBool(wasGameStarted, forKey = KEY_WAS_GAME_STARTED)
    }

    override fun clearSession() {
        defaults.removeObjectForKey(KEY_SESSION_TOKEN)
        defaults.removeObjectForKey(KEY_WAS_GAME_STARTED)
    }

    private companion object {
        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_WAS_GAME_STARTED = "was_game_started"
    }
}

actual fun createPlayerNameStore(): PlayerNameStore = UserDefaultsPlayerNameStore()

actual fun createReconnectSessionStore(): ReconnectSessionStore =
    UserDefaultsReconnectSessionStore()
