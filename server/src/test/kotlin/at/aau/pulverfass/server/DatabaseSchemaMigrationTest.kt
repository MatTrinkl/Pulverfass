package at.aau.pulverfass.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DatabaseSchemaMigrationTest {
    @Test
    fun `migrateDatabaseSchema returns early when database config is disabled`() {
        migrateDatabaseSchema(DatabaseRuntimeConfig())
    }

    @Test
    fun `migrateDatabaseSchema fails fast for unsupported configured jdbc url`() {
        assertThrows(org.flywaydb.core.api.FlywayException::class.java) {
            migrateDatabaseSchema(
                DatabaseRuntimeConfig(
                    url = "jdbc:unsupported://localhost/pulverfass",
                    user = "pulverfass",
                    password = "secret",
                ),
            )
        }
    }

    @Test
    fun `redactJdbcUrl masks authority credentials`() {
        val jdbcUrl = "jdbc:postgresql://pulverfass:secret@db.internal:5432/pulverfass"

        assertEquals(
            "jdbc:postgresql://****:****@db.internal:5432/pulverfass",
            redactJdbcUrl(jdbcUrl),
        )
    }

    @Test
    fun `redactJdbcUrl masks password query parameters`() {
        val jdbcUrl =
            "jdbc:postgresql://db.internal:5432/pulverfass" +
                "?user=pulverfass&password=secret&sslmode=require"

        assertEquals(
            "jdbc:postgresql://db.internal:5432/pulverfass" +
                "?user=pulverfass&password=****&sslmode=require",
            redactJdbcUrl(jdbcUrl),
        )
    }

    @Test
    fun `redactJdbcUrl leaves non sensitive urls unchanged`() {
        val jdbcUrl = "jdbc:postgresql://db.internal:5432/pulverfass"

        assertEquals(jdbcUrl, redactJdbcUrl(jdbcUrl))
    }
}
