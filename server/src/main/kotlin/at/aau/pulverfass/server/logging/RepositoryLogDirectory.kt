package at.aau.pulverfass.server.logging

import ch.qos.logback.core.PropertyDefinerBase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Ermittelt den gemeinsamen Log-Ordner des Repositorys.
 *
 * Der Server kann aus unterschiedlichen Working Directories gestartet werden,
 * zum Beispiel aus IntelliJ im Root oder über Gradle im Servermodul. Die
 * Auflösung läuft deshalb vom aktuellen Arbeitsverzeichnis nach oben bis zum
 * Repository-Root.
 */
object RepositoryLogDirectory {
    /**
     * Liefert den Pfad zum repository-weiten `logs`-Verzeichnis.
     */
    fun resolve(): Path = resolveRepositoryRoot().resolve("logs")

    private fun resolveRepositoryRoot(): Path {
        val workingDirectory =
            Paths
                .get(System.getProperty("user.dir"))
                .toAbsolutePath()
                .normalize()

        return generateSequence(workingDirectory) { current -> current.parent }
            .firstOrNull(::isRepositoryRoot)
            ?: workingDirectory
    }

    private fun isRepositoryRoot(path: Path): Boolean =
        Files.isRegularFile(path.resolve("settings.gradle.kts")) &&
            Files.isDirectory(path.resolve("server")) &&
            Files.isDirectory(path.resolve("shared"))
}

/**
 * Logback-PropertyDefiner für den dynamisch ermittelten Log-Ordner.
 *
 * Die Logback-Konfiguration verwendet diese Klasse, damit File-Appender
 * unabhängig vom aktuellen Working Directory auf denselben Ordner zeigen.
 */
class RepositoryLogDirectoryPropertyDefiner : PropertyDefinerBase() {
    override fun getPropertyValue(): String = RepositoryLogDirectory.resolve().toString()
}
