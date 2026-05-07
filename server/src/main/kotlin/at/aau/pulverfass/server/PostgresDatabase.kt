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
    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                poolName = "pulverfass-server-pool"
                jdbcUrl = config.requireJdbcUrl()
                username = config.requireUser()
                password = config.requirePassword()
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = config.poolMaxSize
                minimumIdle = 0
                connectionTimeout = config.connectionTimeoutMillis
                validationTimeout = config.validationTimeoutMillis
                initializationFailTimeout = -1
                addDataSourceProperty("ApplicationName", "pulverfass-server")
                addDataSourceProperty("tcpKeepAlive", "true")
            },
        )

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
