package at.aau.pulverfass.server

private val semVerTagPattern = Regex("""^v\d+\.\d+\.\d+$""")
private val plainSemVerPattern = Regex("""^\d+\.\d+\.\d+$""")
private val commitShaPattern = Regex("""^[0-9a-fA-F]{7,40}$""")

enum class BuildVersionSource {
    RELEASE_TAG,
    COMMIT_SHA,
    ENVIRONMENT,
    MANIFEST,
    DEFAULT,
}

data class BuildVersion(
    val value: String,
    val source: BuildVersionSource,
) {
    companion object {
        const val DEFAULT_VALUE = "dev"

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

        fun fromReleaseTag(tag: String): BuildVersion {
            val normalizedTag = tag.trim()
            require(semVerTagPattern.matches(normalizedTag)) {
                "Release tag must match vMAJOR.MINOR.PATCH."
            }
            return BuildVersion(normalizedTag, BuildVersionSource.RELEASE_TAG)
        }

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

        fun fromManifestVersion(version: String): BuildVersion =
            BuildVersion(
                value = normalizeRuntimeVersion(version),
                source = BuildVersionSource.MANIFEST,
            )

        fun default(): BuildVersion = BuildVersion(DEFAULT_VALUE, BuildVersionSource.DEFAULT)

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
