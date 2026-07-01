package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.server.session.Session
import at.aau.pulverfass.server.session.SessionReconnectContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant

class JdbcLobbyReconnectSessionStoreTest {
    @Test
    fun `loadSession returns null when token is not persisted`() {
        val query =
            FakePreparedStatementScript(
                expectedSqlFragment = "FROM lobby_reconnect_sessions WHERE session_token_hash = ?",
                rows = emptyList(),
            )
        val dataSource = FakeJdbcDataSource(listOf(FakeConnectionScript(listOf(query))))
        val store = JdbcLobbyReconnectSessionStore(dataSource)
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174900")

        val loaded = store.loadSession(sessionToken)

        assertNull(loaded)
        assertEquals(sessionToken.storageHashForTest(), query.lastParameters[1])
    }

    @Test
    fun `loadSession maps nullable lobby and revoked timestamp fields`() {
        val expiresAt = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        val query =
            FakePreparedStatementScript(
                expectedSqlFragment = "FROM lobby_reconnect_sessions WHERE session_token_hash = ?",
                rows =
                    listOf(
                        mapOf(
                            "player_id" to 41L,
                            "lobby_code" to " ",
                            "player_display_name" to "Alice",
                            "expires_at" to expiresAt,
                            "revoked_at" to null,
                        ),
                    ),
            )
        val store =
            JdbcLobbyReconnectSessionStore(
                FakeJdbcDataSource(listOf(FakeConnectionScript(listOf(query)))),
            )

        val loaded = store.loadSession(SessionToken("123e4567-e89b-12d3-a456-426614174901"))

        assertEquals(
            SessionReconnectContext(
                playerId = PlayerId(41),
                lobbyCode = null,
                playerDisplayName = "Alice",
            ),
            loaded?.context,
        )
        assertEquals(expiresAt.toInstant().toEpochMilli(), loaded?.expiresAtEpochMillis)
        assertNull(loaded?.revokedAtEpochMillis)
    }

    @Test
    fun `loadContext delegates to loadSession context projection`() {
        val query =
            FakePreparedStatementScript(
                expectedSqlFragment = "FROM lobby_reconnect_sessions WHERE session_token_hash = ?",
                rows =
                    listOf(
                        mapOf(
                            "player_id" to 7L,
                            "lobby_code" to "1003",
                            "player_display_name" to "Bob",
                            "expires_at" to Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                            "revoked_at" to Timestamp.from(Instant.parse("2026-01-01T00:01:00Z")),
                        ),
                    ),
            )
        val store =
            JdbcLobbyReconnectSessionStore(
                FakeJdbcDataSource(listOf(FakeConnectionScript(listOf(query)))),
            )

        val context = store.loadContext(SessionToken("123e4567-e89b-12d3-a456-426614174902"))

        assertEquals(
            SessionReconnectContext(
                playerId = PlayerId(7),
                lobbyCode = LobbyCode("1003"),
                playerDisplayName = "Bob",
            ),
            context,
        )
    }

    @Test
    fun `upsertSession deletes prior tokens inserts current session and commits`() {
        val delete =
            FakePreparedStatementScript(
                expectedSqlFragment = "DELETE FROM lobby_reconnect_sessions",
                updateCount = 1,
            )
        val insert =
            FakePreparedStatementScript(
                expectedSqlFragment = "INSERT INTO lobby_reconnect_sessions",
                updateCount = 1,
            )
        val connection = FakeConnectionScript(listOf(delete, insert))
        val store = JdbcLobbyReconnectSessionStore(FakeJdbcDataSource(listOf(connection)))
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174903")
        val session =
            Session(
                sessionToken = sessionToken,
                connectionId = ConnectionId(4),
                expiresAtEpochMillis = 1_700_000_000_000,
            )
        val context =
            SessionReconnectContext(
                playerId = PlayerId(11),
                lobbyCode = LobbyCode("1003"),
                playerDisplayName = "Alice",
            )

        store.upsertSession(session, context)

        assertTrue(connection.committed)
        assertTrue(!connection.rolledBack)
        assertTrue(connection.autoCommit)
        assertEquals(11L, delete.lastParameters[1])
        assertEquals(sessionToken.storageHashForTest(), delete.lastParameters[2])
        assertEquals(sessionToken.storageHashForTest(), insert.lastParameters[1])
        assertEquals(11L, insert.lastParameters[2])
        assertEquals("1003", insert.lastParameters[3])
        assertEquals("Alice", insert.lastParameters[4])
        assertEquals(
            Timestamp.from(Instant.ofEpochMilli(1_700_000_000_000)),
            insert.lastParameters[5],
        )
        assertNull(insert.lastParameters[6])
    }

    @Test
    fun `upsertSession rolls back transaction and restores autoCommit on statement failure`() {
        val delete =
            FakePreparedStatementScript(
                expectedSqlFragment = "DELETE FROM lobby_reconnect_sessions",
                updateCount = 1,
            )
        val insert =
            FakePreparedStatementScript(
                expectedSqlFragment = "INSERT INTO lobby_reconnect_sessions",
                failure = SQLException("boom"),
            )
        val connection = FakeConnectionScript(listOf(delete, insert))
        val store = JdbcLobbyReconnectSessionStore(FakeJdbcDataSource(listOf(connection)))

        val exception =
            assertThrows(SQLException::class.java) {
                store.upsertSession(
                    session =
                        Session(
                            sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174904"),
                            connectionId = ConnectionId(5),
                            expiresAtEpochMillis = 1_700_000_000_100,
                        ),
                    context =
                        SessionReconnectContext(
                            playerId = PlayerId(12),
                            lobbyCode = LobbyCode("1071"),
                            playerDisplayName = "Carol",
                        ),
                )
            }

        assertEquals("boom", exception.message)
        assertTrue(connection.rolledBack)
        assertTrue(!connection.committed)
        assertTrue(connection.autoCommit)
    }

    @Test
    fun `upsertSession rejects missing player id before opening a database connection`() {
        val dataSource = FakeJdbcDataSource(emptyList())
        val store = JdbcLobbyReconnectSessionStore(dataSource)

        assertThrows(IllegalArgumentException::class.java) {
            store.upsertSession(
                session =
                    Session(
                        sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174905"),
                        connectionId = ConnectionId(6),
                        expiresAtEpochMillis = 1_700_000_000_200,
                    ),
                context =
                    SessionReconnectContext(
                        playerId = null,
                        lobbyCode = LobbyCode("1132"),
                        playerDisplayName = "Dora",
                    ),
            )
        }

        assertEquals(0, dataSource.openedConnections)
    }

    @Test
    fun `clear delete and max lookup delegate to sql statements`() {
        val clear =
            FakePreparedStatementScript(
                expectedSqlFragment = "UPDATE lobby_reconnect_sessions",
                updateCount = 2,
            )
        val delete =
            FakePreparedStatementScript(
                expectedSqlFragment = "DELETE FROM lobby_reconnect_sessions",
                updateCount = 1,
            )
        val maxQuery =
            FakePreparedStatementScript(
                expectedSqlFragment = "SELECT COALESCE(MAX(player_id), 0)",
                rows = listOf(mapOf(1 to 17L)),
            )
        val store =
            JdbcLobbyReconnectSessionStore(
                FakeJdbcDataSource(
                    listOf(
                        FakeConnectionScript(listOf(clear)),
                        FakeConnectionScript(listOf(delete)),
                        FakeConnectionScript(listOf(maxQuery)),
                    ),
                ),
            )

        assertEquals(2, store.clearLobbyContextForLobby(LobbyCode("1178")))
        assertEquals("1178", clear.lastParameters[1])
        assertEquals(1, store.deleteSession(SessionToken("123e4567-e89b-12d3-a456-426614174906")))
        assertEquals(
            SessionToken("123e4567-e89b-12d3-a456-426614174906").storageHashForTest(),
            delete.lastParameters[1],
        )
        assertEquals(17L, store.maxPersistedPlayerId())
    }

    private fun SessionToken.storageHashForTest(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
