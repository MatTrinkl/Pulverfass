package at.aau.pulverfass.server.session

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.lobby.normalizePlayerDisplayNameOrFallback
import java.util.concurrent.ConcurrentHashMap

/**
 * Hält reconnect-relevanten Fachkontext stabil pro Session.
 */
class SessionContextRegistry(
    private val persistenceHooks: SessionContextPersistenceHooks =
        SessionContextPersistenceHooks(),
) {
    private val contextsBySession = ConcurrentHashMap<SessionToken, SessionReconnectContext>()
    private val sessionsByPlayer = ConcurrentHashMap<PlayerId, SessionToken>()
    private val lifecycleLock = Any()

    /**
     * Ordnet einer Session dauerhaft einen Player zu.
     */
    fun assignPlayer(
        sessionToken: SessionToken,
        playerId: PlayerId,
    ) {
        synchronized(lifecycleLock) {
            val previousSessionToken = sessionsByPlayer[playerId]
            if (previousSessionToken != null && previousSessionToken != sessionToken) {
                contextsBySession.remove(previousSessionToken)
                persistenceHooks.removeSession(previousSessionToken)
            }

            val current = currentOrLoaded(sessionToken) ?: SessionReconnectContext()
            current.playerId?.let { previousPlayerId ->
                if (previousPlayerId != playerId) {
                    sessionsByPlayer.remove(previousPlayerId)
                }
            }

            val updated = current.copy(playerId = playerId)
            contextsBySession[sessionToken] = updated
            sessionsByPlayer[playerId] = sessionToken
            persistenceHooks.persistContext(sessionToken, updated)
        }
    }

    /**
     * Aktualisiert den Lobby-Kontext einer Session nach erfolgreichem Join.
     */
    fun updateLobbyContext(
        sessionToken: SessionToken,
        lobbyCode: LobbyCode,
        playerDisplayName: String,
    ) {
        synchronized(lifecycleLock) {
            val current = currentOrLoaded(sessionToken) ?: SessionReconnectContext()
            val updated =
                current.copy(
                    lobbyCode = lobbyCode,
                    playerDisplayName = normalizePlayerDisplayNameOrFallback(playerDisplayName),
                )
            contextsBySession[sessionToken] = updated
            if (updated.playerId != null) {
                persistenceHooks.persistContext(sessionToken, updated)
            }
        }
    }

    /**
     * Entfernt den aktuellen Lobby-Kontext einer Session.
     */
    fun clearLobbyContext(sessionToken: SessionToken) {
        synchronized(lifecycleLock) {
            val current = currentOrLoaded(sessionToken) ?: return
            val updated =
                current.copy(
                    lobbyCode = null,
                    playerDisplayName = null,
                )
            contextsBySession[sessionToken] = updated
            if (updated.playerId != null) {
                persistenceHooks.persistContext(sessionToken, updated)
            }
        }
    }

    fun clearLobbyContextForLobby(lobbyCode: LobbyCode) {
        synchronized(lifecycleLock) {
            contextsBySession.replaceAll { _, context ->
                if (context.lobbyCode == lobbyCode) {
                    context.copy(
                        lobbyCode = null,
                        playerDisplayName = null,
                    )
                } else {
                    context
                }
            }
        }
        persistenceHooks.clearLobbyContextForLobby(lobbyCode)
    }

    /**
     * Entfernt alle Daten einer Session vollständig.
     */
    fun removeSession(sessionToken: SessionToken) {
        synchronized(lifecycleLock) {
            val removed = currentOrLoaded(sessionToken)
            contextsBySession.remove(sessionToken)
            removed?.playerId?.let { playerId ->
                if (sessionsByPlayer[playerId] == sessionToken) {
                    sessionsByPlayer.remove(playerId)
                }
            }
            persistenceHooks.removeSession(sessionToken)
        }
    }

    /**
     * Liefert den reconnect-relevanten Kontext einer Session oder `null`.
     */
    fun contextFor(sessionToken: SessionToken): SessionReconnectContext? =
        synchronized(lifecycleLock) { currentOrLoaded(sessionToken) }

    /**
     * Liefert den Player einer Session oder `null`.
     */
    fun playerIdForSession(sessionToken: SessionToken): PlayerId? =
        synchronized(lifecycleLock) { currentOrLoaded(sessionToken)?.playerId }

    /**
     * Liefert die Session eines Players oder `null`.
     */
    fun sessionTokenForPlayer(playerId: PlayerId): SessionToken? = sessionsByPlayer[playerId]

    private fun currentOrLoaded(sessionToken: SessionToken): SessionReconnectContext? {
        contextsBySession[sessionToken]?.let { return it }

        val loaded = persistenceHooks.loadContext(sessionToken)?.normalized() ?: return null
        val existing = contextsBySession.putIfAbsent(sessionToken, loaded) ?: loaded
        existing.playerId?.let { playerId ->
            sessionsByPlayer[playerId] = sessionToken
        }
        return existing
    }

    private fun SessionReconnectContext.normalized(): SessionReconnectContext =
        copy(playerDisplayName = playerDisplayName?.let(::normalizePlayerDisplayNameOrFallback))
}

data class SessionContextPersistenceHooks(
    val loadContext: (SessionToken) -> SessionReconnectContext? = { null },
    val persistContext: (SessionToken, SessionReconnectContext) -> Unit = { _, _ -> },
    val removeSession: (SessionToken) -> Unit = {},
    val clearLobbyContextForLobby: (LobbyCode) -> Unit = {},
)
