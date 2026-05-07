package at.aau.pulverfass.server

data class ServerRuntimeConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val database: DatabaseRuntimeConfig = DatabaseRuntimeConfig(),
    val appVersion: String = BuildVersion.DEFAULT_VALUE,
) {
    companion object {
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
        ): Long {
            if (rawValue == null) {
                return defaultValue
            }

            val parsedValue = rawValue.toLongOrNull()
            require(parsedValue != null && parsedValue > 0) {
                "$variableName must be a positive integer."
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

        private fun readManifestVersion(): String? = ServerRuntimeConfig::class.java.`package`?.implementationVersion
    }
}

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

    val jdbcUrl: String?
        get() =
            url
                ?: if (!host.isNullOrBlank() && !name.isNullOrBlank()) {
                    "jdbc:postgresql://$host:$port/$name"
                } else {
                    null
                }

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

    fun requireJdbcUrl(): String = requireNotNull(jdbcUrl) { "Database JDBC URL is not configured." }

    fun requireUser(): String = requireNotNull(user) { "Database user is not configured." }

    fun requirePassword(): String = requireNotNull(password) { "Database password is not configured." }
}

private const val DEFAULT_DB_PORT = 5432
private const val DEFAULT_DB_POOL_MAX_SIZE = 10
private const val DEFAULT_DB_CONNECTION_TIMEOUT_MILLIS = 5_000L
private const val DEFAULT_DB_VALIDATION_TIMEOUT_MILLIS = 2_000L
