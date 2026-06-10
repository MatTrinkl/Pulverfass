package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConnectionStatusTest {
    @Test
    fun `should serialize all supported connection states`() {
        ConnectionStatus.entries.forEach { status ->
            val serialized = Json.encodeToString(ConnectionStatus.serializer(), status)
            val deserialized = Json.decodeFromString<ConnectionStatus>(serialized)

            assertEquals("\"${status.name}\"", serialized)
            assertEquals(status, deserialized)
        }
    }
}
