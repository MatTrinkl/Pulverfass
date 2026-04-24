package at.aau.pulverfass.server.map

import at.aau.pulverfass.shared.map.config.MapConfigLoader
import at.aau.pulverfass.shared.map.config.MapDefinition

/**
 * Zentrale Read-Sicht auf geladene Map-Definitionen des Servers.
 *
 * Der aktuelle Scope enthält genau eine Default-Map, die beim Startup
 * geladen und für alle neuen Lobbys wiederverwendet wird.
 */
fun interface MapDefinitionRepository {
    fun defaultMapDefinition(): MapDefinition
}

/**
 * Klassenpfad-basierte Repository-Implementierung für die Default-Map.
 */
class ClasspathMapDefinitionRepository private constructor(
    private val defaultDefinition: MapDefinition,
) : MapDefinitionRepository {
    override fun defaultMapDefinition(): MapDefinition = defaultDefinition

    companion object {
        fun loadDefault(
            classLoader: ClassLoader = defaultClassLoader(),
        ): ClasspathMapDefinitionRepository =
            ClasspathMapDefinitionRepository(
                defaultDefinition = MapConfigLoader.loadDefault(classLoader),
            )

        private fun defaultClassLoader(): ClassLoader =
            ClasspathMapDefinitionRepository::class.java.classLoader
                ?: throw IllegalStateException(
                    "Kein ClassLoader für ClasspathMapDefinitionRepository verfügbar.",
                )
    }
}
