package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.server.DatabaseRuntimeConfig
import at.aau.pulverfass.server.assumeDockerAvailableForTestcontainers
import at.aau.pulverfass.server.migrateDatabaseSchema
import at.aau.pulverfass.server.session.Session
import at.aau.pulverfass.server.session.SessionReconnectContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLobbyReconnectSessionStoreIntegrationTest {
    private lateinit var store: JdbcLobbyReconnectSessionStore
    private lateinit var databaseConfig: TestDatabaseConfig
    private var managedContainer: PostgreSQLContainer<*>? = null

    @BeforeAll
    fun migrateSchema() {
        databaseConfig = externalDatabaseConfig() ?: startManagedContainer()
        migrateDatabaseSchema(
            DatabaseRuntimeConfig(
                url = databaseConfig.jdbcUrl,
                user = databaseConfig.username,
                password = databaseConfig.password,
            ),
        )
        store = JdbcLobbyReconnectSessionStore(createDataSource())
    }

    @BeforeEach
    fun clearTables() {
        createDataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    TRUNCATE TABLE lobby_events, lobby_snapshots, lobby_reconnect_sessions
                    RESTART IDENTITY
                    """.trimIndent(),
                )
            }
        }
    }

    @AfterAll
    fun stopManagedContainer() {
        managedContainer?.stop()
    }

    @Test
    fun `upsertSession persists hashed token context and validity`() {
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174451")
        val session =
            Session(
                sessionToken = sessionToken,
                connectionId = ConnectionId(1),
                expiresAtEpochMillis = 1_700_000_000_000,
            )
        val context =
            SessionReconnectContext(
                playerId = PlayerId(11),
                lobbyCode = LobbyCode("1003"),
                playerDisplayName = "Alice",
            )

        store.upsertSession(session, context)

        val persisted = store.loadSession(sessionToken)
        assertEquals(context, persisted?.context)
        assertEquals(session.expiresAtEpochMillis, persisted?.expiresAtEpochMillis)
        assertEquals(null, persisted?.revokedAtEpochMillis)
        assertEquals(11L, store.maxPersistedPlayerId())
    }

    @Test
    fun `clearLobbyContextForLobby removes only lobby binding but keeps player identity`() {
        val firstToken = SessionToken("123e4567-e89b-12d3-a456-426614174452")
        val secondToken = SessionToken("123e4567-e89b-12d3-a456-426614174453")
        store.upsertSession(
            Session(
                sessionToken = firstToken,
                connectionId = ConnectionId(2),
                expiresAtEpochMillis = 1_700_000_000_100,
            ),
            SessionReconnectContext(
                playerId = PlayerId(12),
                lobbyCode = LobbyCode("1071"),
                playerDisplayName = "Bob",
            ),
        )
        store.upsertSession(
            Session(
                sessionToken = secondToken,
                connectionId = ConnectionId(3),
                expiresAtEpochMillis = 1_700_000_000_200,
            ),
            SessionReconnectContext(
                playerId = PlayerId(13),
                lobbyCode = LobbyCode("1132"),
                playerDisplayName = "Carol",
            ),
        )

        store.clearLobbyContextForLobby(LobbyCode("1071"))

        assertEquals(
            SessionReconnectContext(playerId = PlayerId(12)),
            store.loadContext(firstToken),
        )
        assertEquals(
            SessionReconnectContext(
                playerId = PlayerId(13),
                lobbyCode = LobbyCode("1132"),
                playerDisplayName = "Carol",
            ),
            store.loadContext(secondToken),
        )
    }

    @Test
    fun `deleteSession removes persisted reconnect token completely`() {
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174454")
        store.upsertSession(
            Session(
                sessionToken = sessionToken,
                connectionId = ConnectionId(4),
                expiresAtEpochMillis = 1_700_000_000_300,
            ),
            SessionReconnectContext(
                playerId = PlayerId(14),
                lobbyCode = LobbyCode("1178"),
                playerDisplayName = "Dora",
            ),
        )

        store.deleteSession(sessionToken)

        assertNull(store.loadContext(sessionToken))
    }

    @Test
    fun `upsertSession persists revoked timestamp for restart safe reconnect validation`() {
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174455")

        store.upsertSession(
            Session(
                sessionToken = sessionToken,
                connectionId = null,
                expiresAtEpochMillis = 1_700_000_000_400,
                revokedAtEpochMillis = 1_700_000_000_350,
            ),
            SessionReconnectContext(
                playerId = PlayerId(15),
                lobbyCode = LobbyCode("1199"),
                playerDisplayName = "Eve",
            ),
        )

        val persisted = store.loadSession(sessionToken)

        assertEquals(1_700_000_000_350, persisted?.revokedAtEpochMillis)
    }

    private fun createDataSource() =
        PGSimpleDataSource().apply {
            setURL(databaseConfig.jdbcUrl)
            user = databaseConfig.username
            password = databaseConfig.password
        }

    private fun externalDatabaseConfig(): TestDatabaseConfig? {
        val jdbcUrl = System.getenv("PULVERFASS_TEST_DB_URL")?.trim().orEmpty()
        val username = System.getenv("PULVERFASS_TEST_DB_USER")?.trim().orEmpty()
        val password = System.getenv("PULVERFASS_TEST_DB_PASSWORD")?.trim().orEmpty()
        if (jdbcUrl.isEmpty() && username.isEmpty() && password.isEmpty()) {
            return null
        }
        require(jdbcUrl.isNotEmpty()) {
            "PULVERFASS_TEST_DB_URL muss gesetzt sein, wenn eine externe Test-DB genutzt wird."
        }
        require(username.isNotEmpty()) {
            "PULVERFASS_TEST_DB_USER muss gesetzt sein, wenn eine externe Test-DB genutzt wird."
        }
        return TestDatabaseConfig(
            jdbcUrl = jdbcUrl,
            username = username,
            password = password,
        )
    }

    private fun startManagedContainer(): TestDatabaseConfig {
        assumeDockerAvailableForTestcontainers()
        val container = PostgreSQLContainer("postgres:17-alpine")
        container.start()
        managedContainer = container
        return TestDatabaseConfig(
            jdbcUrl = container.jdbcUrl,
            username = container.username,
            password = container.password,
        )
    }

    private data class TestDatabaseConfig(
        val jdbcUrl: String,
        val username: String,
        val password: String,
    )
}
