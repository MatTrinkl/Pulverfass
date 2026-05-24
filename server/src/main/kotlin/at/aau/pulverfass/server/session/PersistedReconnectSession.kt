package at.aau.pulverfass.server.session

/**
 * Persistierter Reconnect-Zustand einer Session.
 *
 * Der eigentliche Session-Token bleibt ein reines Bearer-Secret und wird
 * deshalb nicht im Klartext gespeichert. Für Restart-Recovery genügt der
 * zugehörige Fachkontext plus die zuletzt bekannte Gültigkeit der Session.
 */
data class PersistedReconnectSession(
    val context: SessionReconnectContext,
    val expiresAtEpochMillis: Long,
    val revokedAtEpochMillis: Long? = null,
)
