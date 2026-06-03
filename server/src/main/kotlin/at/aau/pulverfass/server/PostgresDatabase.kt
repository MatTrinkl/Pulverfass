package at.aau.pulverfass.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.SQLException
import kotlin.math.max

/**
 * Grober Gesundheitszustand einer optionalen Datenbankintegration.
 */
enum class DatabaseReadinessState {
    UP,
    DOWN,
    DISABLED,
}

/**
 * Ergebnis einer Datenbank-Readiness-Prüfung.
 *
 * @property state technischer Status der geprüften Datenbankanbindung
 * @property detail optionale Zusatzinformation für Health-Checks und Logs
 */
data class DatabaseReadiness(
    val state: DatabaseReadinessState,
    val detail: String? = null,
) {
    /**
     * `true`, wenn die Datenbankverbindung aktuell nutzbar ist.
     */
    val isReady: Boolean
        get() = state == DatabaseReadinessState.UP
}

/**
 * Abstraktion für Health-Checks gegen die persistente Infrastruktur.
 *
 * Implementierungen können echte Datenbankverbindungen prüfen oder einen deaktivierten Zustand
 * repräsentieren, wenn das Backend bewusst ohne Persistenz gestartet wurde.
 */
interface DatabaseReadinessProbe : AutoCloseable {
    /**
     * Führt den aktuellen Health-Check aus.
     *
     * @return Ergebnis der Prüfung
     */
    fun readiness(): DatabaseReadiness

    override fun close() = Unit

    companion object {
        /**
         * Liefert einen Probe-Stub für Deployments ohne Datenbankkonfiguration.
         */
        fun disabled(): DatabaseReadinessProbe = DisabledDatabaseReadinessProbe
    }
}

/**
 * Fasst mehrere Datenbank- oder Persistence-Probes zu einem Health-Check zusammen.
 *
 * Ein einzelner `DOWN`-Status dominiert, `UP` signalisiert mindestens eine nutzbare
 * Datenbankintegration und `DISABLED` bedeutet, dass alle Teilprobes deaktiviert sind.
 *
 * @param probes Teilprobes, die gemeinsam ausgewertet werden
 */
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

    /**
     * Prüft, ob eine Verbindung aus dem Pool bezogen und per JDBC als gültig verifiziert werden
     * kann.
     *
     * @return `UP`, wenn der PostgreSQL-Pool nutzbar ist, sonst `DOWN`
     */
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

/**
 * Erstellt den HikariCP-DataSource für die produktive PostgreSQL-Anbindung des Servers.
 *
 * Der Pool ist so konfiguriert, dass der Server auch bei kurzzeitig nicht erreichbarer Datenbank
 * starten kann; Health-Checks und Persistenzpfade melden den Fehler dann zur Laufzeit.
 *
 * @param config validierte Datenbankkonfiguration
 * @param poolName Name des Hikari-Pools für Logs und Metriken
 * @param applicationName `ApplicationName`, das PostgreSQL in Sessions und Logs ausweist
 * @return initialisierte [HikariDataSource]
 * @throws IllegalArgumentException wenn Pflichtwerte in [config] fehlen
 */
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
            // Der Server soll auch dann booten, wenn PostgreSQL erst kurz nach dem Prozessstart
            // erreichbar wird; der Health-Check deckt den Zustand separat ab.
            initializationFailTimeout = -1
            addDataSourceProperty("ApplicationName", applicationName)
            addDataSourceProperty("tcpKeepAlive", "true")
        },
    )
