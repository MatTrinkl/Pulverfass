package at.aau.pulverfass.server

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

private val migrationLogger =
    LoggerFactory.getLogger(
        "at.aau.pulverfass.server.DatabaseSchemaMigration",
    )

fun migrateDatabaseSchema(config: DatabaseRuntimeConfig) {
    if (!config.isConfigured) {
        migrationLogger.info(
            "Skipping Flyway migration because database configuration is disabled.",
        )
        return
    }

    migrationLogger.info(
        "Running Flyway migrations for database {}",
        config.jdbcUrl,
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
