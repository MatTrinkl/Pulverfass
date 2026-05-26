package at.aau.pulverfass.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

class PostgresDatabaseReadinessProbeTest {
    private var managedContainer: PostgreSQLContainer<*>? = null

    @AfterEach
    fun tearDown() {
        managedContainer?.stop()
        managedContainer = null
    }

    @Test
    fun `composite readiness probe returns down when one probe is down`() {
        val composite =
            CompositeDatabaseReadinessProbe(
                StaticProbe(DatabaseReadiness(DatabaseReadinessState.UP)),
                StaticProbe(
                    DatabaseReadiness(
                        state = DatabaseReadinessState.DOWN,
                        detail = "Connection refused",
                    ),
                ),
            )

        assertEquals(
            DatabaseReadiness(
                state = DatabaseReadinessState.DOWN,
                detail = "Connection refused",
            ),
            composite.readiness(),
        )
    }

    @Test
    fun `composite readiness probe returns up when at least one probe is up`() {
        val composite =
            CompositeDatabaseReadinessProbe(
                StaticProbe(
                    DatabaseReadiness(
                        state = DatabaseReadinessState.DISABLED,
                        detail = "Database is not configured.",
                    ),
                ),
                StaticProbe(DatabaseReadiness(DatabaseReadinessState.UP)),
            )

        assertEquals(
            DatabaseReadiness(DatabaseReadinessState.UP),
            composite.readiness(),
        )
    }

    @Test
    fun `composite readiness probe returns disabled detail when no probe is up`() {
        val composite =
            CompositeDatabaseReadinessProbe(
                StaticProbe(
                    DatabaseReadiness(
                        state = DatabaseReadinessState.DISABLED,
                        detail = "Database is not configured.",
                    ),
                ),
                StaticProbe(DatabaseReadiness(DatabaseReadinessState.DISABLED)),
            )

        assertEquals(
            DatabaseReadiness(
                state = DatabaseReadinessState.DISABLED,
                detail = "Database is not configured.",
            ),
            composite.readiness(),
        )
    }

    @Test
    fun `composite readiness probe closes all delegates`() {
        val firstProbe = CloseTrackingProbe(DatabaseReadiness(DatabaseReadinessState.UP))
        val secondProbe = CloseTrackingProbe(DatabaseReadiness(DatabaseReadinessState.DISABLED))
        val composite = CompositeDatabaseReadinessProbe(firstProbe, secondProbe)

        composite.close()

        assertTrue(firstProbe.closed)
        assertTrue(secondProbe.closed)
    }

    @Test
    fun `postgres readiness probe reports up for reachable database`() {
        val databaseConfig = testDatabaseConfig()
        val probe = PostgresDatabaseReadinessProbe(databaseConfig)

        try {
            assertEquals(
                DatabaseReadinessState.UP,
                probe.readiness().state,
            )
        } finally {
            probe.close()
        }
    }

    @Test
    fun `postgres readiness probe reports down for unreachable database`() {
        val databaseConfig =
            DatabaseRuntimeConfig(
                host = "127.0.0.1",
                port = 1,
                name = "pulverfass_test",
                user = "postgres",
                password = "postgres",
                connectionTimeoutMillis = 250,
                validationTimeoutMillis = 250,
            )
        val probe = PostgresDatabaseReadinessProbe(databaseConfig)

        try {
            val readiness = probe.readiness()

            assertEquals(DatabaseReadinessState.DOWN, readiness.state)
            assertFalse(readiness.detail.isNullOrBlank())
        } finally {
            probe.close()
        }
    }

    private fun testDatabaseConfig(): DatabaseRuntimeConfig {
        externalDatabaseConfig()?.let { return it }
        assumeDockerAvailableForTestcontainers()

        val container =
            PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("pulverfass_test")
                .withUsername("postgres")
                .withPassword("postgres")
        container.start()
        managedContainer = container
        return DatabaseRuntimeConfig(
            url = container.jdbcUrl,
            user = container.username,
            password = container.password,
        )
    }

    private fun externalDatabaseConfig(): DatabaseRuntimeConfig? {
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

        return DatabaseRuntimeConfig(
            url = jdbcUrl,
            user = username,
            password = password,
        )
    }

    private class StaticProbe(
        private val readiness: DatabaseReadiness,
    ) : DatabaseReadinessProbe {
        override fun readiness(): DatabaseReadiness = readiness
    }

    private class CloseTrackingProbe(
        private val readiness: DatabaseReadiness,
    ) : DatabaseReadinessProbe {
        var closed: Boolean = false
            private set

        override fun readiness(): DatabaseReadiness = readiness

        override fun close() {
            closed = true
        }
    }
}
