package at.aau.pulverfass.server.session

import at.aau.pulverfass.server.ids.DuplicateConnectionIdException
import at.aau.pulverfass.server.ids.SessionConnectionNotFoundException
import at.aau.pulverfass.server.ids.SessionTokenNotFoundException
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.response.ReconnectErrorCode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Zentrale Verwaltung technischer Sessions.
 *
 * Eine Session besitzt einen stabilen [SessionToken] und kann später erneut an
 * eine neue [ConnectionId] gebunden werden.
 */
class SessionManager(
    private val sessionTtlMillis: Long = DEFAULT_SESSION_TTL_MILLIS,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val tokenFactory: () -> SessionToken = {
        SessionToken(UUID.randomUUID().toString())
    },
) {
    private val sessionsByToken = ConcurrentHashMap<SessionToken, Session>()
    private val tokensByConnection = ConcurrentHashMap<ConnectionId, SessionToken>()
    private val lifecycleLock = Any()

    init {
        require(sessionTtlMillis > 0) {
            "sessionTtlMillis muss positiv sein, war aber $sessionTtlMillis."
        }
    }

    /**
     * Erstellt eine neue Session und bindet sie sofort an die angegebene Verbindung.
     */
    fun createSession(connectionId: ConnectionId): Session {
        synchronized(lifecycleLock) {
            if (tokensByConnection.containsKey(connectionId)) {
                throw DuplicateConnectionIdException(connectionId)
            }

            while (true) {
                val token = tokenFactory()
                if (sessionsByToken.containsKey(token)) {
                    continue
                }

                val session =
                    Session(
                        sessionToken = token,
                        connectionId = connectionId,
                        expiresAtEpochMillis = expiresAtEpochMillis(),
                    )
                sessionsByToken[token] = session
                tokensByConnection[connectionId] = token
                return session
            }
        }
    }

    /**
     * Bindet eine bestehende Session an eine neue aktive Verbindung.
     */
    fun bindExisting(
        sessionToken: SessionToken,
        connectionId: ConnectionId,
    ): Session =
        synchronized(lifecycleLock) {
            if (tokensByConnection.containsKey(connectionId)) {
                throw DuplicateConnectionIdException(connectionId)
            }

            val current = requireByToken(sessionToken)
            current.connectionId?.let { previousConnectionId ->
                tokensByConnection.remove(previousConnectionId)
            }

            val updated =
                current.copy(
                    connectionId = connectionId,
                    expiresAtEpochMillis = expiresAtEpochMillis(),
                )
            sessionsByToken[sessionToken] = updated
            tokensByConnection[connectionId] = sessionToken
            updated
        }

    /**
     * Liefert die Session einer aktiven Verbindung oder `null`.
     */
    fun getByConnectionId(connectionId: ConnectionId): Session? =
        tokensByConnection[connectionId]?.let { token -> sessionsByToken[token] }

    /**
     * Liefert eine Session über ihren stabilen Token oder `null`.
     */
    fun getByToken(sessionToken: SessionToken): Session? = sessionsByToken[sessionToken]

    /**
     * Prüft, ob ein Token aktuell für einen Reconnect verwendbar ist.
     */
    fun reconnectErrorFor(sessionToken: SessionToken): ReconnectErrorCode? =
        synchronized(lifecycleLock) {
            val session = sessionsByToken[sessionToken] ?: return ReconnectErrorCode.TOKEN_INVALID
            when {
                session.isRevoked -> ReconnectErrorCode.TOKEN_REVOKED
                session.isExpired(nowEpochMillis()) -> ReconnectErrorCode.TOKEN_EXPIRED
                else -> null
            }
        }

    /**
     * Liefert die Session zu einer aktiven Verbindung oder wirft.
     */
    fun requireByConnectionId(connectionId: ConnectionId): Session =
        getByConnectionId(connectionId) ?: throw SessionConnectionNotFoundException(connectionId)

    /**
     * Liefert die Session zu einem Token oder wirft.
     */
    fun requireByToken(sessionToken: SessionToken): Session =
        sessionsByToken[sessionToken] ?: throw SessionTokenNotFoundException(sessionToken)

    /**
     * Invalidiert eine Session dauerhaft für spätere Reconnect-Versuche.
     */
    fun invalidate(sessionToken: SessionToken): Session? =
        synchronized(lifecycleLock) {
            val session = sessionsByToken[sessionToken] ?: return null
            session.connectionId?.let(tokensByConnection::remove)
            val invalidated =
                session.copy(
                    connectionId = null,
                    revokedAtEpochMillis = nowEpochMillis(),
                )
            sessionsByToken[sessionToken] = invalidated
            invalidated
        }

    /**
     * Entfernt die Session einer Verbindung vollständig.
     *
     * Diese Operation wird für provisorische Sessions benötigt, die durch einen
     * erfolgreichen Reconnect ersetzt werden.
     */
    fun removeByConnectionId(connectionId: ConnectionId): Session? =
        synchronized(lifecycleLock) {
            val sessionToken = tokensByConnection.remove(connectionId) ?: return null
            sessionsByToken.remove(sessionToken)
        }

    /**
     * Löst die aktuelle Verbindung von einer Session, behält den Token aber.
     */
    fun detachConnection(connectionId: ConnectionId): Session? =
        synchronized(lifecycleLock) {
            val sessionToken = tokensByConnection.remove(connectionId) ?: return null
            val session = sessionsByToken[sessionToken] ?: return null
            val detached = session.copy(connectionId = null)
            sessionsByToken[sessionToken] = detached
            detached
        }

    private fun expiresAtEpochMillis(): Long = nowEpochMillis() + sessionTtlMillis

    companion object {
        const val DEFAULT_SESSION_TTL_MILLIS: Long = 5 * 60 * 1000
    }
}
