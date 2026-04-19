package at.aau.pulverfass.server.session

import at.aau.pulverfass.server.ids.DuplicateConnectionIdException
import at.aau.pulverfass.server.ids.SessionConnectionNotFoundException
import at.aau.pulverfass.server.ids.SessionTokenNotFoundException
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.SessionToken
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Zentrale Verwaltung technischer Sessions.
 *
 * Eine Session besitzt einen stabilen [SessionToken] und kann später erneut an
 * eine neue [ConnectionId] gebunden werden.
 */
class SessionManager(
    private val tokenFactory: () -> SessionToken = {
        SessionToken(UUID.randomUUID().toString())
    },
) {
    private val sessionsByToken = ConcurrentHashMap<SessionToken, Session>()
    private val tokensByConnection = ConcurrentHashMap<ConnectionId, SessionToken>()

    /**
     * Erstellt eine neue Session und bindet sie sofort an die angegebene Verbindung.
     */
    fun createSession(connectionId: ConnectionId): Session {
        if (tokensByConnection.containsKey(connectionId)) {
            throw DuplicateConnectionIdException(connectionId)
        }

        while (true) {
            val token = tokenFactory()
            val session = Session(sessionToken = token, connectionId = connectionId)
            if (sessionsByToken.putIfAbsent(token, session) == null) {
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
    ): Session {
        if (tokensByConnection.containsKey(connectionId)) {
            throw DuplicateConnectionIdException(connectionId)
        }

        val current = requireByToken(sessionToken)
        current.connectionId?.let { previousConnectionId ->
            tokensByConnection.remove(previousConnectionId)
        }

        val updated = current.copy(connectionId = connectionId)
        sessionsByToken[sessionToken] = updated
        tokensByConnection[connectionId] = sessionToken
        return updated
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
     * Löst die aktuelle Verbindung von einer Session, behält den Token aber.
     */
    fun detachConnection(connectionId: ConnectionId): Session? {
        val sessionToken = tokensByConnection.remove(connectionId) ?: return null
        val session = sessionsByToken[sessionToken] ?: return null
        val detached = session.copy(connectionId = null)
        sessionsByToken[sessionToken] = detached
        return detached
    }
}
