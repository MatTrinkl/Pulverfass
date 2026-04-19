package at.aau.pulverfass.shared.ids

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SerializationTest {
    private val json = Json

    @Test
    fun `playerId sollte korrekt serialisiert und deserialisiert werden`() {
        val original = PlayerId(37)

        val encoded = json.encodeToString(PlayerId.serializer(), original)
        val decoded = json.decodeFromString(PlayerId.serializer(), encoded)

        assertEquals("37", encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `entityId sollte korrekt serialisiert und deserialisiert werden`() {
        val original = EntityId(100)

        val encoded = json.encodeToString(EntityId.serializer(), original)
        val decoded = json.decodeFromString(EntityId.serializer(), encoded)

        assertEquals("100", encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `connectionId sollte korrekt serialisiert und deserialisiert werden`() {
        val original = ConnectionId(7)

        val encoded = json.encodeToString(ConnectionId.serializer(), original)
        val decoded = json.decodeFromString(ConnectionId.serializer(), encoded)

        assertEquals("7", encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `sessionToken sollte korrekt serialisiert und deserialisiert werden`() {
        val original = SessionToken("123e4567-e89b-12d3-a456-426614174100")

        val encoded = json.encodeToString(SessionToken.serializer(), original)
        val decoded = json.decodeFromString(SessionToken.serializer(), encoded)

        assertEquals("\"123e4567-e89b-12d3-a456-426614174100\"", encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `gameId sollte korrekt serialisiert und deserialisiert werden`() {
        val original = GameId(999)

        val encoded = json.encodeToString(GameId.serializer(), original)
        val decoded = json.decodeFromString(GameId.serializer(), encoded)

        assertEquals("999", encoded)
        assertEquals(original, decoded)
    }
}
