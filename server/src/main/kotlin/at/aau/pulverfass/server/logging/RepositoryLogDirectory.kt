package at.aau.pulverfass.server.logging

import ch.qos.logback.core.PropertyDefinerBase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves server log files to the repository-level logs directory.
 */
object RepositoryLogDirectory {
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

class RepositoryLogDirectoryPropertyDefiner : PropertyDefinerBase() {
    override fun getPropertyValue(): String = RepositoryLogDirectory.resolve().toString()
}
