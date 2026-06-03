package at.aau.pulverfass.shared.map.config

import at.aau.pulverfass.shared.text.decodeUtf8Strict
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.nio.charset.CharacterCodingException

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
        try {
            loadFromJson(inputStream.readBytes().decodeUtf8Strict())
        } catch (cause: CharacterCodingException) {
            throw MapConfigLoadException(
                "Map-Config konnte nicht als UTF-8 gelesen werden: ${cause.message}",
                cause,
            )
        }

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
