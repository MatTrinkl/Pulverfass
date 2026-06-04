package at.aau.pulverfass.server

/**
 * Zusammengefasste Laufzeitkonfiguration des Backends.
 *
 * Die Konfiguration wird zentral beim Prozessstart aus Umgebungsvariablen aufgelöst und anschließend
 * an Server, WebSocket-Transport und Persistenz weitergereicht.
 *
 * @property host Hostname oder Bind-Adresse des Ktor-Servers
 * @property port TCP-Port des Ktor-Servers
 * @property database optionale Datenbankkonfiguration für Persistenz und Recovery
 * @property webSocketMaxFrameSizeBytes maximale Größe eingehender WebSocket-Frames
 * @property appVersion nach außen sichtbare Build-Version des Servers
 */
data class ServerRuntimeConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val database: DatabaseRuntimeConfig = DatabaseRuntimeConfig(),
    val webSocketMaxFrameSizeBytes: Long = WebSocketPolicy.MAX_FRAME_SIZE_BYTES,
    val appVersion: String = BuildVersion.DEFAULT_VALUE,
) {
    companion object {
        /**
         * Erzeugt die Serverkonfiguration aus Umgebungsvariablen und optionalem Manifest.
         *
         * Unterstützt sowohl eine vollständige JDBC-URL als auch Host-/Port-/DB-Name-Kombinationen
         * für PostgreSQL. Fehlkonfigurationen werden früh per `require` signalisiert.
         *
         * @param environment Quellmenge der Umgebungsvariablen
         * @param manifestVersionProvider liefert optional die Manifest-Version des Build-Artefakts
         * @return vollständig aufgelöste Laufzeitkonfiguration
         * @throws IllegalArgumentException bei ungültigen Zahlenwerten oder inkonsistenter
         * Datenbankkonfiguration
         */
        fun fromEnvironment(
            environment: Map<String, String> = System.getenv(),
            manifestVersionProvider: () -> String? = ::readManifestVersion,
        ): ServerRuntimeConfig =
            ServerRuntimeConfig(
                host = environment.optionalValue("HOST") ?: DEFAULT_HOST,
                port = parsePort(environment.optionalValue("PORT")),
                database =
                    DatabaseRuntimeConfig(
                        url = environment.optionalValue("DB_URL"),
                        host = environment.optionalValue("DB_HOST"),
                        port =
                            parsePort(
                                environment.optionalValue("DB_PORT"),
                                defaultValue = DEFAULT_DB_PORT,
                                variableName = "DB_PORT",
                            ),
                        name = environment.optionalValue("DB_NAME"),
                        user = environment.optionalValue("DB_USER"),
                        password = environment.optionalValue("DB_PASSWORD"),
                        poolMaxSize =
                            parsePositiveInt(
                                environment.optionalValue("DB_POOL_MAX_SIZE"),
                                defaultValue = DEFAULT_DB_POOL_MAX_SIZE,
                                variableName = "DB_POOL_MAX_SIZE",
                            ),
                        connectionTimeoutMillis =
                            parsePositiveLong(
                                environment.optionalValue("DB_CONNECTION_TIMEOUT_MS"),
                                defaultValue = DEFAULT_DB_CONNECTION_TIMEOUT_MILLIS,
                                variableName = "DB_CONNECTION_TIMEOUT_MS",
                            ),
                        validationTimeoutMillis =
                            parsePositiveLong(
                                environment.optionalValue("DB_VALIDATION_TIMEOUT_MS"),
                                defaultValue = DEFAULT_DB_VALIDATION_TIMEOUT_MILLIS,
                                variableName = "DB_VALIDATION_TIMEOUT_MS",
                            ),
                    ),
                webSocketMaxFrameSizeBytes =
                    parsePositiveLong(
                        environment.optionalValue("WS_MAX_FRAME_SIZE_BYTES"),
                        defaultValue = WebSocketPolicy.MAX_FRAME_SIZE_BYTES,
                        variableName = "WS_MAX_FRAME_SIZE_BYTES",
                        maxValue = Int.MAX_VALUE.toLong(),
                    ),
                appVersion =
                    resolveAppVersion(
                        environment.optionalValue("APP_VERSION"),
                        manifestVersionProvider,
                    ).value,
            )

        private fun parsePort(
            rawPort: String?,
            defaultValue: Int = DEFAULT_PORT,
            variableName: String = "PORT",
        ): Int {
            if (rawPort == null) {
                return defaultValue
            }

            val parsedPort = rawPort.toIntOrNull()
            require(parsedPort != null && parsedPort in 1..65_535) {
                "$variableName must be an integer between 1 and 65535."
            }
            return parsedPort
        }

        private fun Map<String, String>.optionalValue(key: String): String? =
            get(key)?.trim()?.takeIf(String::isNotEmpty)

        private fun parsePositiveInt(
            rawValue: String?,
            defaultValue: Int,
            variableName: String,
        ): Int {
            if (rawValue == null) {
                return defaultValue
            }

            val parsedValue = rawValue.toIntOrNull()
            require(parsedValue != null && parsedValue > 0) {
                "$variableName must be a positive integer."
            }
            return parsedValue
        }

        private fun parsePositiveLong(
            rawValue: String?,
            defaultValue: Long,
            variableName: String,
            maxValue: Long? = null,
        ): Long {
            if (rawValue == null) {
                return defaultValue
            }

            val parsedValue = rawValue.toLongOrNull()
            require(parsedValue != null && parsedValue > 0) {
                "$variableName must be a positive integer."
            }
            require(maxValue == null || parsedValue <= maxValue) {
                "$variableName must be less than or equal to $maxValue."
            }
            return parsedValue
        }

        private fun resolveAppVersion(
            envVersion: String?,
            manifestVersionProvider: () -> String?,
        ): BuildVersion =
            envVersion
                ?.let(BuildVersion::normalizeRuntimeVersion)
                ?.let { normalizedVersion ->
                    BuildVersion(
                        value = normalizedVersion,
                        source = BuildVersionSource.ENVIRONMENT,
                    )
                }
                ?: manifestVersionProvider()
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let(BuildVersion::fromManifestVersion)
                ?: BuildVersion.default()

        private fun readManifestVersion(): String? =
            ServerRuntimeConfig::class.java.`package`?.implementationVersion
    }
}

/**
 * Optionale PostgreSQL-Konfiguration des Backends.
 *
 * Die Instanz gilt nur dann als aktiviert, wenn wenigstens ein DB-bezogener Wert gesetzt ist.
 * Sobald Persistenz aktiviert wird, werden Benutzername, Passwort und JDBC-Ziel verbindlich
 * vorausgesetzt.
 *
 * @property url vollständige JDBC-URL; hat Vorrang vor [host], [port] und [name]
 * @property host PostgreSQL-Host für zusammengesetzte JDBC-URLs
 * @property port PostgreSQL-Port für zusammengesetzte JDBC-URLs
 * @property name Datenbankname für zusammengesetzte JDBC-URLs
 * @property user Benutzername für JDBC-Verbindungen
 * @property password Passwort für JDBC-Verbindungen
 * @property poolMaxSize maximale Anzahl gleichzeitiger Pool-Verbindungen
 * @property connectionTimeoutMillis Timeout für das Beziehen einer Verbindung aus dem Pool
 * @property validationTimeoutMillis Timeout für JDBC-Validierungsprüfungen
 */
data class DatabaseRuntimeConfig(
    val url: String? = null,
    val host: String? = null,
    val port: Int = DEFAULT_DB_PORT,
    val name: String? = null,
    val user: String? = null,
    val password: String? = null,
    val poolMaxSize: Int = DEFAULT_DB_POOL_MAX_SIZE,
    val connectionTimeoutMillis: Long = DEFAULT_DB_CONNECTION_TIMEOUT_MILLIS,
    val validationTimeoutMillis: Long = DEFAULT_DB_VALIDATION_TIMEOUT_MILLIS,
) {
    private val hasAnyInput: Boolean
        get() =
            listOf(url, host, name, user, password)
                .any { !it.isNullOrBlank() }

    /**
     * Abgeleitete JDBC-URL oder `null`, wenn die Datenbank nicht vollständig adressierbar ist.
     */
    val jdbcUrl: String?
        get() =
            url
                ?: if (!host.isNullOrBlank() && !name.isNullOrBlank()) {
                    "jdbc:postgresql://$host:$port/$name"
                } else {
                    null
                }

    /**
     * `true`, wenn Persistenz bewusst konfiguriert und vollständig genug für einen Verbindungsaufbau ist.
     *
     * @throws IllegalArgumentException wenn Teilkonfigurationen gesetzt wurden, Pflichtwerte aber
     * fehlen
     */
    val isConfigured: Boolean
        get() {
            if (!hasAnyInput) {
                return false
            }

            require(!user.isNullOrBlank()) {
                "DB_USER must be set when database configuration is provided."
            }
            require(!password.isNullOrBlank()) {
                "DB_PASSWORD must be set when database configuration is provided."
            }
            require(jdbcUrl != null) {
                "Configure DB_URL or the combination of DB_HOST and DB_NAME."
            }
            return true
        }

    /**
     * Liefert die JDBC-URL oder bricht mit einer sprechenden Fehlermeldung ab.
     *
     * @throws IllegalArgumentException wenn keine JDBC-URL aufgelöst werden konnte
     */
    fun requireJdbcUrl(): String =
        requireNotNull(jdbcUrl) {
            "Database JDBC URL is not configured."
        }

    /**
     * Liefert den Datenbankbenutzer oder bricht mit einer sprechenden Fehlermeldung ab.
     */
    fun requireUser(): String =
        requireNotNull(user) {
            "Database user is not configured."
        }

    /**
     * Liefert das Datenbankpasswort oder bricht mit einer sprechenden Fehlermeldung ab.
     */
    fun requirePassword(): String =
        requireNotNull(password) {
            "Database password is not configured."
        }
}

private const val DEFAULT_DB_PORT = 5432
private const val DEFAULT_DB_POOL_MAX_SIZE = 10
private const val DEFAULT_DB_CONNECTION_TIMEOUT_MILLIS = 5_000L
private const val DEFAULT_DB_VALIDATION_TIMEOUT_MILLIS = 2_000L
