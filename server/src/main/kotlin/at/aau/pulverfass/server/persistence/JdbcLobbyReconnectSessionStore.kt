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

/**
 * JDBC-basierter Store für wiederverwendbare Reconnect-Sessions.
 *
 * Gespeichert werden nur die für eine Wiederaufnahme benötigten Kontextdaten. Das Session-Token
 * selbst wird nie im Klartext persistiert, sondern vor der Ablage gehasht.
 *
 * @param dataSource Datenquelle für PostgreSQL-Zugriffe
 */
class JdbcLobbyReconnectSessionStore(
    private val dataSource: DataSource,
) {
    /**
     * Lädt eine persistierte Session anhand ihres Tokens.
     *
     * @return persistierte Session oder `null`, wenn kein Eintrag existiert
     */
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

    /**
     * Lädt nur den fachlichen Reconnect-Kontext zu einem Token.
     */
    fun loadContext(sessionToken: SessionToken): SessionReconnectContext? =
        loadSession(sessionToken)?.context

    /**
     * Legt eine Session an oder aktualisiert sie atomar.
     *
     * Pro Spieler bleibt höchstens ein gültiges Session-Token erhalten. Vor dem Upsert werden
     * deshalb ältere Tokens desselben Spielers entfernt.
     *
     * @throws IllegalArgumentException wenn [context] keine `playerId` enthält
     */
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
                // Ein Spieler darf genau ein wiederverwendbares Token besitzen, damit spätere
                // Reconnect-Versuche deterministisch dem neuesten Login folgen.
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

    /**
     * Entfernt den fachlichen Lobby-Kontext aller Sessions einer Lobby, ohne die Tokens selbst zu löschen.
     *
     * Das wird beim Schließen einer Lobby genutzt, damit alte Tokens nicht versehentlich in eine
     * nicht mehr existente Lobby zurückführen.
     *
     * @return Anzahl aktualisierter Zeilen
     */
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

    /**
     * Löscht genau eine persistierte Session.
     *
     * @return Anzahl gelöschter Zeilen
     */
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

    /**
     * Liefert die höchste bislang persistierte Spieler-ID.
     *
     * Der Server nutzt diesen Wert beim Neustart, um neue Spieler-IDs ohne Kollision zu vergeben.
     */
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

/**
 * Führt mehrere Reconnect-Session-Schreibzugriffe als gemeinsame JDBC-Transaktion aus.
 */
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

/**
 * Leitet aus einem Session-Token einen stabilen Speicher-Hash ab.
 *
 * Der Klartextwert bleibt damit außerhalb des Arbeitsspeichers unbekannt, was Datenbankleaks
 * weniger kritisch macht.
 */
private fun SessionToken.storageHash(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun Long.asTimestamp(): Timestamp = Timestamp.from(Instant.ofEpochMilli(this))
