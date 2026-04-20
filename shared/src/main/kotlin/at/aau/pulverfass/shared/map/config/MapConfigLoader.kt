package at.aau.pulverfass.shared.map.config

import java.io.InputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Lädt Map-Konfigurationen aus JSON-Strings oder Klassenpfad-Ressourcen.
 */
object MapConfigLoader {
    const val DEFAULT_RESOURCE_PATH: String = "config/maps/default-map.json"

    private val json =
        Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }

    fun loadDefault(classLoader: ClassLoader = defaultClassLoader()): MapDefinition =
        loadResource(DEFAULT_RESOURCE_PATH, classLoader)

    fun loadResource(
        resourcePath: String,
        classLoader: ClassLoader = defaultClassLoader(),
    ): MapDefinition {
        val normalizedPath = resourcePath.removePrefix("/")
        val inputStream =
            classLoader.getResourceAsStream(normalizedPath)
                ?: throw MapConfigLoadException(
                    "Map-Config-Ressource '$resourcePath' wurde nicht gefunden.",
                )

        return inputStream.use(::load)
    }

    fun load(inputStream: InputStream): MapDefinition =
        loadFromJson(inputStream.readBytes().decodeToString())

    fun loadFromJson(jsonString: String): MapDefinition {
        val rawConfig =
            try {
                json.decodeFromString(MapConfig.serializer(), jsonString)
            } catch (cause: SerializationException) {
                throw MapConfigLoadException(
                    "Map-Config konnte nicht geparst werden: ${cause.message}",
                    cause,
                )
            }

        return MapConfigValidator.validate(rawConfig)
    }

    private fun defaultClassLoader(): ClassLoader =
        MapConfigLoader::class.java.classLoader
            ?: throw MapConfigLoadException("Kein ClassLoader für MapConfigLoader verfügbar.")
}
