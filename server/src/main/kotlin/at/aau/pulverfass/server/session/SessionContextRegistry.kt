package at.aau.pulverfass.server.session

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import java.util.concurrent.ConcurrentHashMap

/**
 * Hält reconnect-relevanten Fachkontext stabil pro Session.
 */
class SessionContextRegistry {
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
            }

            val current = contextsBySession[sessionToken] ?: SessionReconnectContext()
            current.playerId?.let { previousPlayerId ->
                if (previousPlayerId != playerId) {
                    sessionsByPlayer.remove(previousPlayerId)
                }
            }

            contextsBySession[sessionToken] = current.copy(playerId = playerId)
            sessionsByPlayer[playerId] = sessionToken
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
            val current = contextsBySession[sessionToken] ?: SessionReconnectContext()
            contextsBySession[sessionToken] =
                current.copy(
                    lobbyCode = lobbyCode,
                    playerDisplayName = playerDisplayName,
                )
        }
    }

    /**
     * Entfernt den aktuellen Lobby-Kontext einer Session.
     */
    fun clearLobbyContext(sessionToken: SessionToken) {
        synchronized(lifecycleLock) {
            val current = contextsBySession[sessionToken] ?: return
            contextsBySession[sessionToken] =
                current.copy(
                    lobbyCode = null,
                    playerDisplayName = null,
                )
        }
    }

    /**
     * Entfernt alle Daten einer Session vollständig.
     */
    fun removeSession(sessionToken: SessionToken) {
        synchronized(lifecycleLock) {
            val removed = contextsBySession.remove(sessionToken) ?: return
            removed.playerId?.let { playerId ->
                if (sessionsByPlayer[playerId] == sessionToken) {
                    sessionsByPlayer.remove(playerId)
                }
            }
        }
    }

    /**
     * Liefert den reconnect-relevanten Kontext einer Session oder `null`.
     */
    fun contextFor(sessionToken: SessionToken): SessionReconnectContext? =
        contextsBySession[sessionToken]

    /**
     * Liefert den Player einer Session oder `null`.
     */
    fun playerIdForSession(sessionToken: SessionToken): PlayerId? =
        contextsBySession[sessionToken]?.playerId

    /**
     * Liefert die Session eines Players oder `null`.
     */
    fun sessionTokenForPlayer(playerId: PlayerId): SessionToken? = sessionsByPlayer[playerId]
}
