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
    private var onSessionUpsert: (Session) -> Unit = {}
    private var onSessionRemoved: (SessionToken) -> Unit = {}

    init {
        require(sessionTtlMillis > 0) {
            "sessionTtlMillis muss positiv sein, war aber $sessionTtlMillis."
        }
    }

    /**
     * Erstellt eine neue Session und bindet sie sofort an die angegebene Verbindung.
     */
    fun createSession(connectionId: ConnectionId): Session {
        val created =
            synchronized(lifecycleLock) {
                purgeRetiredSessions()

                if (tokensByConnection.containsKey(connectionId)) {
                    throw DuplicateConnectionIdException(connectionId)
                }

                var createdSession: Session? = null
                while (createdSession == null) {
                    val token = tokenFactory()
                    if (sessionsByToken.containsKey(token)) {
                        continue
                    }

                    createdSession =
                        Session(
                            sessionToken = token,
                            connectionId = connectionId,
                            expiresAtEpochMillis = expiresAtEpochMillis(),
                        )
                    sessionsByToken[token] = createdSession
                    tokensByConnection[connectionId] = token
                }
                requireNotNull(createdSession)
            }
        onSessionUpsert(created)
        return created
    }

    /**
     * Bindet eine bestehende Session an eine neue aktive Verbindung.
     */
    fun bindExisting(
        sessionToken: SessionToken,
        connectionId: ConnectionId,
    ): Session =
        synchronized(lifecycleLock) {
            purgeRetiredSessions()

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
        }.also(onSessionUpsert)

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
     * Stellt eine bekannte, aktuell nicht verbundene Session wieder her.
     *
     * Dieser Pfad wird für Restart-Recovery genutzt, wenn der fachliche
     * Reconnect-Kontext persistiert wurde, aber der technische Session-Index
     * beim Boot neu aufgebaut werden musste.
     */
    fun restoreDetachedSession(
        sessionToken: SessionToken,
        expiresAtEpochMillis: Long,
        revokedAtEpochMillis: Long? = null,
    ): Session =
        synchronized(lifecycleLock) {
            purgeRetiredSessions()

            sessionsByToken[sessionToken]?.let { return it }

            val restored =
                Session(
                    sessionToken = sessionToken,
                    connectionId = null,
                    expiresAtEpochMillis = expiresAtEpochMillis,
                    revokedAtEpochMillis = revokedAtEpochMillis,
                )
            sessionsByToken[sessionToken] = restored
            restored
        }.also(onSessionUpsert)

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
    fun invalidate(sessionToken: SessionToken): Session? {
        val invalidated =
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
        onSessionUpsert(invalidated)
        return invalidated
    }

    /**
     * Entfernt die Session einer Verbindung vollständig.
     *
     * Diese Operation wird für provisorische Sessions benötigt, die durch einen
     * erfolgreichen Reconnect ersetzt werden.
     */
    fun removeByConnectionId(connectionId: ConnectionId): Session? =
        synchronized(lifecycleLock) {
            val sessionToken = tokensByConnection.remove(connectionId)
            if (sessionToken == null) {
                null
            } else {
                sessionsByToken.remove(sessionToken)
            }
        }.also { removed ->
            removed?.let { onSessionRemoved(it.sessionToken) }
        }

    /**
     * Löst die aktuelle Verbindung von einer Session, behält den Token aber.
     */
    fun detachConnection(connectionId: ConnectionId): Session? =
        synchronized(lifecycleLock) {
            val sessionToken = tokensByConnection.remove(connectionId)
            if (sessionToken == null) {
                null
            } else {
                val session = sessionsByToken[sessionToken] ?: return@synchronized null
                val detached = session.copy(connectionId = null)
                sessionsByToken[sessionToken] = detached
                detached
            }
        }.also { detached ->
            detached?.let(onSessionUpsert)
        }

    fun installLifecycleHooks(
        onSessionUpsert: (Session) -> Unit = {},
        onSessionRemoved: (SessionToken) -> Unit = {},
    ) {
        this.onSessionUpsert = onSessionUpsert
        this.onSessionRemoved = onSessionRemoved
    }

    /**
     * Entfernt abgelaufene oder bereits invalidierte Sessions ohne aktive
     * Verbindung opportunistisch aus den internen Indizes.
     */
    private fun purgeRetiredSessions() {
        val now = nowEpochMillis()
        val retiredTokens =
            sessionsByToken
                .filterValues { session ->
                    !session.isConnected &&
                        when {
                            session.isRevoked ->
                                now - (session.revokedAtEpochMillis ?: now) >= sessionTtlMillis
                            session.isExpired(now) ->
                                now - session.expiresAtEpochMillis >= sessionTtlMillis
                            else -> false
                        }
                }.keys

        retiredTokens.forEach { sessionToken ->
            sessionsByToken.remove(sessionToken)
            onSessionRemoved(sessionToken)
        }
    }

    private fun expiresAtEpochMillis(): Long = nowEpochMillis() + sessionTtlMillis

    companion object {
        const val DEFAULT_SESSION_TTL_MILLIS: Long = 60 * 60 * 1000
    }
}
