package at.aau.pulverfass.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BuildVersionTest {
    @Test
    fun `fromReleaseTag accepts semver tags`() {
        val version = BuildVersion.fromReleaseTag("v1.2.3")

        assertEquals("v1.2.3", version.value)
        assertEquals(BuildVersionSource.RELEASE_TAG, version.source)
    }

    @Test
    fun `fromReleaseTag rejects invalid tags`() {
        assertThrows(IllegalArgumentException::class.java) {
            BuildVersion.fromReleaseTag("1.2.3")
        }

        assertThrows(IllegalArgumentException::class.java) {
            BuildVersion.fromReleaseTag("v1.2")
        }
    }

    @Test
    fun `fromGitReference uses release tag for tag refs`() {
        val version = BuildVersion.fromGitReference("tag", "v2.4.6", "abcdef1234567")

        assertEquals("v2.4.6", version.value)
        assertEquals(BuildVersionSource.RELEASE_TAG, version.source)
    }

    @Test
    fun `fromGitReference uses short sha for non tag refs`() {
        val version = BuildVersion.fromGitReference("branch", "main", "ABCDEF1234567")

        assertEquals("sha-abcdef1", version.value)
        assertEquals(BuildVersionSource.COMMIT_SHA, version.source)
    }

    @Test
    fun `normalizeRuntimeVersion prefixes manifest semver`() {
        assertEquals("v3.5.8", BuildVersion.normalizeRuntimeVersion("3.5.8"))
        assertEquals("sha-abcdef1", BuildVersion.normalizeRuntimeVersion("sha-abcdef1"))
    }
}
