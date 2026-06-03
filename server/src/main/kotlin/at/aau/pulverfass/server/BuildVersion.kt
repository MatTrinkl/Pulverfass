package at.aau.pulverfass.server

private val semVerTagPattern = Regex("""^v\d+\.\d+\.\d+$""")
private val plainSemVerPattern = Regex("""^\d+\.\d+\.\d+$""")
private val commitShaPattern = Regex("""^[0-9a-fA-F]{7,40}$""")

/**
 * Herkunft einer zur Laufzeit aufgelösten Build-Version.
 *
 * Der Server verwendet diese Information für Diagnosezwecke, damit in Logs und
 * Statusendpunkten nachvollziehbar bleibt, ob die angezeigte Version aus dem
 * Release-Tag, dem Commit, der Umgebung oder dem Manifest stammt.
 */
enum class BuildVersionSource {
    RELEASE_TAG,
    COMMIT_SHA,
    ENVIRONMENT,
    MANIFEST,
    DEFAULT,
}

/**
 * Normalisierte, für Logs und API-Antworten geeignete Build-Version des Servers.
 *
 * @property value formatierter Versionsstring, zum Beispiel `v1.2.3`, `sha-1a2b3c4`
 * oder `dev`
 * @property source technische Herkunft des Versionswerts
 */
data class BuildVersion(
    val value: String,
    val source: BuildVersionSource,
) {
    companion object {
        const val DEFAULT_VALUE = "dev"

        /**
         * Leitet die auszuliefernde Build-Version aus einer GitHub-ähnlichen Ref-Angabe ab.
         *
         * Tags werden als Release-Version interpretiert, alle anderen Refs fallen auf den
         * Commit-SHA zurück.
         *
         * @param refType Typ des Refs, typischerweise `tag` oder `branch`
         * @param refName Name des Refs
         * @param commitSha Commit-SHA des Builds
         * @return normalisierte Build-Version
         * @throws IllegalArgumentException wenn Tag oder Commit-SHA nicht dem erwarteten Format
         * entsprechen
         */
        fun fromGitReference(
            refType: String,
            refName: String,
            commitSha: String,
        ): BuildVersion =
            if (refType == "tag") {
                fromReleaseTag(refName)
            } else {
                fromCommitSha(commitSha)
            }

        /**
         * Erzeugt eine Build-Version aus einem Release-Tag im Format `vMAJOR.MINOR.PATCH`.
         *
         * @param tag Git-Tag des Releases
         * @return normalisierte Release-Version
         * @throws IllegalArgumentException wenn [tag] nicht dem erwarteten SemVer-Format folgt
         */
        fun fromReleaseTag(tag: String): BuildVersion {
            val normalizedTag = tag.trim()
            require(semVerTagPattern.matches(normalizedTag)) {
                "Release tag must match vMAJOR.MINOR.PATCH."
            }
            return BuildVersion(normalizedTag, BuildVersionSource.RELEASE_TAG)
        }

        /**
         * Erzeugt eine abgekürzte Build-Version aus einem Commit-SHA.
         *
         * @param commitSha vollständiger oder kurzer Git-Commit-SHA
         * @return Version im Format `sha-<7 hex>`
         * @throws IllegalArgumentException wenn [commitSha] keine hexadezimale SHA-Darstellung ist
         */
        fun fromCommitSha(commitSha: String): BuildVersion {
            val normalizedSha = commitSha.trim().lowercase()
            require(commitShaPattern.matches(normalizedSha)) {
                "Commit SHA must be 7 to 40 hexadecimal characters."
            }
            return BuildVersion(
                value = "sha-${normalizedSha.take(7)}",
                source = BuildVersionSource.COMMIT_SHA,
            )
        }

        /**
         * Erzeugt eine Build-Version aus dem Manifest-Eintrag eines gepackten Artefakts.
         *
         * Reine SemVer-Werte ohne führendes `v` werden dabei vereinheitlicht.
         *
         * @param version im Manifest hinterlegte Versionsangabe
         * @return normalisierte Manifest-Version
         * @throws IllegalArgumentException wenn [version] leer oder nur Whitespace ist
         */
        fun fromManifestVersion(version: String): BuildVersion =
            BuildVersion(
                value = normalizeRuntimeVersion(version),
                source = BuildVersionSource.MANIFEST,
            )

        /**
         * Liefert den Entwicklungs-Fallback für lokale oder unvollständig konfigurierte Builds.
         */
        fun default(): BuildVersion = BuildVersion(DEFAULT_VALUE, BuildVersionSource.DEFAULT)

        /**
         * Vereinheitlicht eine Laufzeitversionsangabe für Logs und Diagnosen.
         *
         * Reine SemVer-Werte werden mit einem führenden `v` ergänzt; alle anderen nichtleeren
         * Strings bleiben erhalten.
         *
         * @param version rohe Versionsangabe aus Umgebung oder Manifest
         * @return normalisierte Versionsdarstellung
         * @throws IllegalArgumentException wenn [version] leer oder nur Whitespace ist
         */
        fun normalizeRuntimeVersion(version: String): String {
            val normalizedVersion = version.trim()
            require(normalizedVersion.isNotEmpty()) {
                "Version must not be blank."
            }

            return if (plainSemVerPattern.matches(normalizedVersion)) {
                "v$normalizedVersion"
            } else {
                normalizedVersion
            }
        }
    }
}
