package at.aau.pulverfass.shared.ids

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SerializationTest {

    private val json = Json

    @Test
    fun `playerId should serialize and deserialize correctly`() {
        val original = PlayerId(37)

        val encoded = json.encodeToString(PlayerId.serializer(), original)
        val decoded = json.decodeFromString(PlayerId.serializer(), encoded)

        assertEquals(original, decoded)
    }
}
