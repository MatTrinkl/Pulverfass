package at.aau.pulverfass.server

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

private val migrationLogger =
    LoggerFactory.getLogger(
        "at.aau.pulverfass.server.DatabaseSchemaMigration",
    )

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
        .migrate()
}

/**
 * Entfernt Benutzername- und Passwortfragmente aus einer JDBC-URL für Log-Ausgaben.
 */
internal fun redactJdbcUrl(jdbcUrl: String): String =
    jdbcUrl
        .replace(Regex("(//)([^/@:]+):([^/@]+)@"), "$1****:****@")
        .replace(Regex("([?&](?:password|pass|pwd)=)[^&]+", RegexOption.IGNORE_CASE), "$1****")
