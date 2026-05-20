package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.server.session.PersistedReconnectSession
import at.aau.pulverfass.server.session.Session
import at.aau.pulverfass.server.session.SessionReconnectContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcLobbyReconnectSessionStore(
    private val dataSource: DataSource,
) {
    fun loadSession(sessionToken: SessionToken): PersistedReconnectSession? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT player_id, lobby_code, player_display_name, expires_at, revoked_at
                FROM lobby_reconnect_sessions
                WHERE session_token_hash = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, sessionToken.storageHash())
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return@use null
                    }
                    PersistedReconnectSession(
                        context =
                            SessionReconnectContext(
                                playerId = PlayerId(resultSet.getLong("player_id")),
                                lobbyCode =
                                    resultSet.getString("lobby_code")
                                        ?.takeIf(String::isNotBlank)
                                        ?.let(::LobbyCode),
                                playerDisplayName = resultSet.getString("player_display_name"),
                            ),
                        expiresAtEpochMillis =
                            resultSet.getTimestamp("expires_at").toInstant().toEpochMilli(),
                        revokedAtEpochMillis =
                            resultSet.getTimestamp("revoked_at")?.toInstant()?.toEpochMilli(),
                    )
                }
            }
        }

    fun loadContext(sessionToken: SessionToken): SessionReconnectContext? =
        loadSession(sessionToken)?.context

    fun upsertSession(
        session: Session,
        context: SessionReconnectContext,
    ) {
        val playerId =
            requireNotNull(context.playerId) {
                "Persistierter SessionReconnectContext benötigt eine playerId."
            }

        dataSource.connection.use { connection ->
            connection.inReconnectSessionTransaction {
                connection.prepareStatement(
                    """
                    DELETE FROM lobby_reconnect_sessions
                    WHERE player_id = ?
                      AND session_token_hash <> ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, playerId.value)
                    statement.setString(2, session.sessionToken.storageHash())
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    INSERT INTO lobby_reconnect_sessions (
                        session_token_hash,
                        player_id,
                        lobby_code,
                        player_display_name,
                        expires_at,
                        revoked_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (session_token_hash) DO UPDATE SET
                        player_id = EXCLUDED.player_id,
                        lobby_code = EXCLUDED.lobby_code,
                        player_display_name = EXCLUDED.player_display_name,
                        expires_at = EXCLUDED.expires_at,
                        revoked_at = EXCLUDED.revoked_at,
                        updated_at = NOW()
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, session.sessionToken.storageHash())
                    statement.setLong(2, playerId.value)
                    statement.setString(3, context.lobbyCode?.value)
                    statement.setString(4, context.playerDisplayName)
                    statement.setTimestamp(5, session.expiresAtEpochMillis.asTimestamp())
                    statement.setTimestamp(6, session.revokedAtEpochMillis?.asTimestamp())
                    statement.executeUpdate()
                }
                1
            }
        }
    }

    fun clearLobbyContextForLobby(lobbyCode: LobbyCode): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE lobby_reconnect_sessions
                SET lobby_code = NULL,
                    player_display_name = NULL,
                    updated_at = NOW()
                WHERE lobby_code = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, lobbyCode.value)
                statement.executeUpdate()
            }
        }

    fun deleteSession(sessionToken: SessionToken): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                DELETE FROM lobby_reconnect_sessions
                WHERE session_token_hash = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, sessionToken.storageHash())
                statement.executeUpdate()
            }
        }

    fun maxPersistedPlayerId(): Long =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT COALESCE(MAX(player_id), 0)
                FROM lobby_reconnect_sessions
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    resultSet.getLong(1)
                }
            }
        }
}

private fun Connection.inReconnectSessionTransaction(block: () -> Int): Int {
    val previousAutoCommit = autoCommit
    autoCommit = false
    return try {
        val updatedRows = block()
        commit()
        updatedRows
    } catch (exception: Exception) {
        rollback()
        throw exception
    } finally {
        autoCommit = previousAutoCommit
    }
}

private fun SessionToken.storageHash(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun Long.asTimestamp(): Timestamp = Timestamp.from(Instant.ofEpochMilli(this))
