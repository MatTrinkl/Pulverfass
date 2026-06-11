package at.aau.pulverfass.client.storage

/**
 * Liefert die plattformspezifische Persistenz für Spielername und Charakter
 * (Android: SharedPreferences, iOS: NSUserDefaults).
 */
expect fun createPlayerNameStore(): PlayerNameStore

/**
 * Liefert die plattformspezifische Persistenz für Reconnect-Sessions
 * (Android: SharedPreferences, iOS: NSUserDefaults).
 */
expect fun createReconnectSessionStore(): ReconnectSessionStore
