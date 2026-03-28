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

    @Test
    fun `entityId sollte korrekt serialisiert und deserialisiert werden`() {
        val original = EntityId(100)

        // Objekt wird in JSON umgewandelt
        val encoded = json.encodeToString(EntityId.serializer(), original)
        // JSON wird wieder in ein Objekt umgewandelt
        val decoded = json.decodeFromString(EntityId.serializer(), encoded)

        // Überprüfung: ursprüngliches Objekt und Ergebnis sind gleich
        assertEquals(original, decoded)
    }

    @Test
    fun `connectionId sollte korrekt serialisiert und deserialisiert werden`() {
        val original = ConnectionId(7)

        val encoded = json.encodeToString(ConnectionId.serializer(), original)
        val decoded = json.decodeFromString(ConnectionId.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `gameId sollte korrekt serialisiert und deserialisiert werden`() {
        val original = GameId(999)

        val encoded = json.encodeToString(GameId.serializer(), original)
        val decoded = json.decodeFromString(GameId.serializer(), encoded)

        assertEquals(original, decoded)
    }
}
