package at.aau.pulverfass.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerRuntimeConfigTest {
    @Test
    fun `fromEnvironment uses defaults when env vars are missing`() {
        val config =
            ServerRuntimeConfig.fromEnvironment(
                emptyMap(),
                manifestVersionProvider = { null },
            )

        assertEquals(DEFAULT_HOST, config.host)
        assertEquals(DEFAULT_PORT, config.port)
        assertFalse(config.database.isConfigured)
        assertEquals(WebSocketPolicy.MAX_FRAME_SIZE_BYTES, config.webSocketMaxFrameSizeBytes)
        assertEquals(BuildVersion.DEFAULT_VALUE, config.appVersion)
    }

    @Test
    fun `fromEnvironment reads runtime env vars`() {
        val config =
            ServerRuntimeConfig.fromEnvironment(
                mapOf(
                    "HOST" to "127.0.0.1",
                    "PORT" to "9090",
                    "DB_HOST" to "db",
                    "DB_PORT" to "5433",
                    "DB_NAME" to "pulverfass",
                    "DB_USER" to "pulverfass",
                    "DB_PASSWORD" to "secret",
                    "APP_VERSION" to "v1.2.3",
                    "DB_POOL_MAX_SIZE" to "16",
                    "DB_CONNECTION_TIMEOUT_MS" to "7000",
                    "DB_VALIDATION_TIMEOUT_MS" to "3000",
                    "WS_MAX_FRAME_SIZE_BYTES" to "262144",
                ),
                manifestVersionProvider = { null },
            )

        assertEquals("127.0.0.1", config.host)
        assertEquals(9090, config.port)
        assertEquals("db", config.database.host)
        assertEquals(5433, config.database.port)
        assertEquals("pulverfass", config.database.name)
        assertEquals("jdbc:postgresql://db:5433/pulverfass", config.database.jdbcUrl)
        assertEquals("pulverfass", config.database.user)
        assertEquals("secret", config.database.password)
        assertEquals(16, config.database.poolMaxSize)
        assertEquals(7_000L, config.database.connectionTimeoutMillis)
        assertEquals(3_000L, config.database.validationTimeoutMillis)
        assertEquals(262_144L, config.webSocketMaxFrameSizeBytes)
        assertTrue(config.database.isConfigured)
        assertEquals("v1.2.3", config.appVersion)
    }

    @Test
    fun `fromEnvironment accepts explicit jdbc url`() {
        val config =
            ServerRuntimeConfig.fromEnvironment(
                mapOf(
                    "DB_URL" to "jdbc:postgresql://db/pulverfass",
                    "DB_USER" to "pulverfass",
                    "DB_PASSWORD" to "secret",
                ),
                manifestVersionProvider = { null },
            )

        assertEquals("jdbc:postgresql://db/pulverfass", config.database.jdbcUrl)
        assertTrue(config.database.isConfigured)
    }

    @Test
    fun `fromEnvironment falls back to manifest version`() {
        val config =
            ServerRuntimeConfig.fromEnvironment(
                emptyMap(),
                manifestVersionProvider = { "1.4.2" },
            )

        assertEquals("v1.4.2", config.appVersion)
    }

    @Test
    fun `fromEnvironment rejects invalid port values`() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerRuntimeConfig.fromEnvironment(mapOf("PORT" to "not-a-number"))
        }

        assertThrows(IllegalArgumentException::class.java) {
            ServerRuntimeConfig.fromEnvironment(mapOf("PORT" to "70000"))
        }

        assertThrows(IllegalArgumentException::class.java) {
            ServerRuntimeConfig.fromEnvironment(mapOf("WS_MAX_FRAME_SIZE_BYTES" to "0"))
        }

        assertThrows(IllegalArgumentException::class.java) {
            ServerRuntimeConfig.fromEnvironment(
                mapOf("WS_MAX_FRAME_SIZE_BYTES" to "${Int.MAX_VALUE.toLong() + 1}"),
            )
        }
    }

    @Test
    fun `fromEnvironment rejects incomplete database configuration`() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerRuntimeConfig.fromEnvironment(
                mapOf(
                    "DB_HOST" to "db",
                    "DB_USER" to "pulverfass",
                ),
                manifestVersionProvider = { null },
            ).database.isConfigured
        }
    }
}
