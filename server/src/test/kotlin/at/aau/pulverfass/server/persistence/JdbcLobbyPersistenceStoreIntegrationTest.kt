package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.server.DatabaseRuntimeConfig
import at.aau.pulverfass.server.migrateDatabaseSchema
import at.aau.pulverfass.shared.ids.LobbyCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLobbyPersistenceStoreIntegrationTest {
    private lateinit var store: JdbcLobbyPersistenceStore
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
        store =
            JdbcLobbyPersistenceStore(
                dataSource = createDataSource(),
            )
    }

    @BeforeEach
    fun clearTables() {
        val dataSource = createDataSource()
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "TRUNCATE TABLE lobby_events, lobby_snapshots RESTART IDENTITY",
                )
            }
        }
    }

    @AfterAll
    fun stopManagedContainer() {
        managedContainer?.stop()
    }

    @Test
    fun `event retention keeps only the latest two rounds per lobby`() {
        val targetLobby = LobbyCode("RET1")
        val untouchedLobby = LobbyCode("KEEP")
        var stateVersion = 1L

        store.appendEvent(
            lobbyCode = untouchedLobby,
            stateVersion = 99,
            turnCount = 1,
            eventType = "test.other",
            eventJson = jsonPayload("other"),
        )

        for (round in 0..4) {
            repeat(2) { offset ->
                store.appendEvent(
                    lobbyCode = targetLobby,
                    stateVersion = stateVersion++,
                    turnCount = round,
                    eventType = "test.round.$round",
                    eventJson = jsonPayload("round-$round-event-$offset"),
                )
            }
        }

        val retainedEvents = store.listEvents(targetLobby)
        assertEquals(listOf(3, 3, 4, 4), retainedEvents.map { it.turnCount })
        assertEquals(
            listOf("test.round.3", "test.round.3", "test.round.4", "test.round.4"),
            retainedEvents.map { it.eventType },
        )

        val otherLobbyEvents = store.listEvents(untouchedLobby)
        assertEquals(1, otherLobbyEvents.size)
        assertEquals(1, otherLobbyEvents.single().turnCount)
    }

    @Test
    fun `snapshot retention keeps only the latest four snapshots per lobby`() {
        val targetLobby = LobbyCode("SNP1")
        val untouchedLobby = LobbyCode("SNP2")

        store.appendSnapshot(
            lobbyCode = untouchedLobby,
            stateVersion = 50,
            turnCount = 5,
            snapshotJson = jsonPayload("untouched"),
        )

        for (stateVersion in 1L..6L) {
            store.appendSnapshot(
                lobbyCode = targetLobby,
                stateVersion = stateVersion,
                turnCount = stateVersion.toInt(),
                snapshotJson = jsonPayload("snapshot-$stateVersion"),
            )
        }

        val retainedSnapshots = store.listSnapshots(targetLobby)
        assertEquals(listOf(3L, 4L, 5L, 6L), retainedSnapshots.map { it.stateVersion })
        assertEquals(listOf(3, 4, 5, 6), retainedSnapshots.map { it.turnCount })

        val otherLobbySnapshots = store.listSnapshots(untouchedLobby)
        assertEquals(1, otherLobbySnapshots.size)
        assertEquals(50L, otherLobbySnapshots.single().stateVersion)
    }

    private fun jsonPayload(value: String) =
        buildJsonObject {
            put("value", value)
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
