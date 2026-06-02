package at.aau.pulverfass.server

import at.aau.pulverfass.server.logging.ServerLoggers
import org.flywaydb.core.Flyway

private val migrationLogger = ServerLoggers.technical("DatabaseSchemaMigration")

/**
 * Führt die Flyway-Schemamigration für die konfigurierte PostgreSQL-Datenbank aus.
 *
 * Die Funktion ist bewusst idempotent angelegt: Ohne vollständige Datenbankkonfiguration wird
 * die Migration übersprungen, damit lokale Entwicklungsstarts ohne Persistenz möglich bleiben.
 *
 * @param config aufgelöste Laufzeitkonfiguration für die Datenbank
 * @throws IllegalArgumentException wenn [config] als konfiguriert gilt, aber Pflichtwerte fehlen
 * @throws org.flywaydb.core.api.FlywayException wenn Flyway die Migration nicht anwenden kann
 */
fun migrateDatabaseSchema(config: DatabaseRuntimeConfig) {
    if (!config.isConfigured) {
        migrationLogger.info(
            "Skipping Flyway migration because database configuration is disabled.",
        )
        return
    }

    migrationLogger.info(
        "Running Flyway migrations for database {}",
        redactJdbcUrl(config.requireJdbcUrl()),
    )

    val flyway =
        Flyway
            .configure()
            .dataSource(
                config.requireJdbcUrl(),
                config.requireUser(),
                config.requirePassword(),
            ).locations("classpath:db/migration")
            .cleanDisabled(true)
            .validateOnMigrate(true)
            .load()

    val statusBeforeMigration = flyway.info()
    migrationLogger.info(
        "Flyway status database={} currentVersion={} pendingMigrations={}",
        redactJdbcUrl(config.requireJdbcUrl()),
        statusBeforeMigration.current()?.version?.version ?: "none",
        statusBeforeMigration.pending().size,
    )

    val result =
        flyway
            .migrate()

    val statusAfterMigration = flyway.info()
    migrationLogger.info(
        "Flyway migration completed database={} migrationsExecuted={} " +
            "currentVersion={} pendingMigrations={}",
        redactJdbcUrl(config.requireJdbcUrl()),
        result.migrationsExecuted,
        statusAfterMigration.current()?.version?.version ?: "none",
        statusAfterMigration.pending().size,
    )
}

/**
 * Entfernt Benutzername- und Passwortfragmente aus einer JDBC-URL für Log-Ausgaben.
 */
internal fun redactJdbcUrl(jdbcUrl: String): String =
    jdbcUrl
        .replace(Regex("(//)([^/@:]+):([^/@]+)@"), "$1****:****@")
        .replace(Regex("([?&](?:password|pass|pwd)=)[^&]+", RegexOption.IGNORE_CASE), "$1****")
