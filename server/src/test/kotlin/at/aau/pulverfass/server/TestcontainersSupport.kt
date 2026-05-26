package at.aau.pulverfass.server

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory

internal fun assumeDockerAvailableForTestcontainers() {
    val available =
        runCatching { DockerClientFactory.instance().isDockerAvailable() }
            .getOrDefault(false)
    assumeTrue(available, "Docker is required for Testcontainers-based integration tests.")
}
