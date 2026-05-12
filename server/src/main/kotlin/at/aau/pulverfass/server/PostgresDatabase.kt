package at.aau.pulverfass.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.SQLException
import kotlin.math.max

enum class DatabaseReadinessState {
    UP,
    DOWN,
    DISABLED,
}

data class DatabaseReadiness(
    val state: DatabaseReadinessState,
    val detail: String? = null,
) {
    val isReady: Boolean
        get() = state == DatabaseReadinessState.UP
}

interface DatabaseReadinessProbe : AutoCloseable {
    fun readiness(): DatabaseReadiness

    override fun close() = Unit

    companion object {
        fun disabled(): DatabaseReadinessProbe = DisabledDatabaseReadinessProbe
    }
}

class CompositeDatabaseReadinessProbe(
    private vararg val probes: DatabaseReadinessProbe,
) : DatabaseReadinessProbe {
    override fun readiness(): DatabaseReadiness {
        val readings = probes.map(DatabaseReadinessProbe::readiness)
        val firstDown = readings.firstOrNull { it.state == DatabaseReadinessState.DOWN }
        if (firstDown != null) {
            return firstDown
        }
        if (readings.any { it.state == DatabaseReadinessState.UP }) {
            return DatabaseReadiness(DatabaseReadinessState.UP)
        }
        return DatabaseReadiness(
            state = DatabaseReadinessState.DISABLED,
            detail = readings.firstNotNullOfOrNull(DatabaseReadiness::detail),
        )
    }

    override fun close() {
        probes.forEach(DatabaseReadinessProbe::close)
    }
}

private object DisabledDatabaseReadinessProbe : DatabaseReadinessProbe {
    override fun readiness(): DatabaseReadiness =
        DatabaseReadiness(
            state = DatabaseReadinessState.DISABLED,
            detail = "Database is not configured.",
        )
}

class PostgresDatabaseReadinessProbe(
    private val config: DatabaseRuntimeConfig,
) : DatabaseReadinessProbe {
    private val dataSource = createPostgresDataSource(config, poolName = "pulverfass-server-pool")

    override fun readiness(): DatabaseReadiness =
        try {
            dataSource.connection.use { connection ->
                if (connection.isValid(max(1, (config.validationTimeoutMillis / 1_000L).toInt()))) {
                    DatabaseReadiness(DatabaseReadinessState.UP)
                } else {
                    DatabaseReadiness(
                        state = DatabaseReadinessState.DOWN,
                        detail = "Database connection validation returned false.",
                    )
                }
            }
        } catch (exception: SQLException) {
            DatabaseReadiness(
                state = DatabaseReadinessState.DOWN,
                detail = exception.message ?: exception::class.simpleName ?: "SQL error",
            )
        }

    override fun close() {
        dataSource.close()
    }
}

fun createPostgresDataSource(
    config: DatabaseRuntimeConfig,
    poolName: String,
    applicationName: String = "pulverfass-server",
): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.poolName = poolName
            jdbcUrl = config.requireJdbcUrl()
            username = config.requireUser()
            password = config.requirePassword()
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.poolMaxSize
            minimumIdle = 0
            connectionTimeout = config.connectionTimeoutMillis
            validationTimeout = config.validationTimeoutMillis
            initializationFailTimeout = -1
            addDataSourceProperty("ApplicationName", applicationName)
            addDataSourceProperty("tcpKeepAlive", "true")
        },
    )
