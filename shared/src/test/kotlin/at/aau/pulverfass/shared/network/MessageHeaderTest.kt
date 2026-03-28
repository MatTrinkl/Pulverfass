package at.aau.pulverfass.shared.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageHeaderTest {
    private val json = Json

    @Test
    fun `should create message header correctly`() {
        val header = MessageHeader(type = MessageType.LOGIN_REQUEST)

        assertEquals(MessageType.LOGIN_REQUEST, header.type)
    }

    @Test
    fun `should serialize message header with enum name`() {
        val header = MessageHeader(type = MessageType.CHAT_MESSAGE)

        val serialized = json.encodeToString(header)

        assertEquals("""{"type":"CHAT_MESSAGE"}""", serialized)
    }

    @Test
    fun `should deserialize message header with enum name`() {
        val jsonString = """{"type":"GAME_JOIN_REQUEST"}"""

        val deserialized = json.decodeFromString<MessageHeader>(jsonString)

        assertEquals(MessageHeader(MessageType.GAME_JOIN_REQUEST), deserialized)
    }

    @Test
    fun `should serialize and deserialize correctly`() {
        val header = MessageHeader(type = MessageType.HEARTBEAT)

        val serialized = json.encodeToString(header)
        val deserialized = json.decodeFromString<MessageHeader>(serialized)

        assertEquals(header, deserialized)
    }

    @Test
    fun `should support equality for data class`() {
        val header1 = MessageHeader(MessageType.LOGOUT_REQUEST)
        val header2 = MessageHeader(MessageType.LOGOUT_REQUEST)
        val header3 = MessageHeader(MessageType.LOGIN_REQUEST)

        assertEquals(header1, header2)
        assertNotEquals(header1, header3)
    }

    @Test
    fun `should fail for unknown enum value during deserialization`() {
        val jsonString = """{"type":"NOT_A_REAL_TYPE"}"""

        assertThrows(SerializationException::class.java) {
            json.decodeFromString<MessageHeader>(jsonString)
        }
    }

    @Test
    fun `should contain field name and enum value in json`() {
        val header = MessageHeader(type = MessageType.GAME_CREATE_RESPONSE)

        val jsonString = json.encodeToString(header)

        assertTrue(jsonString.contains("type"))
        assertTrue(jsonString.contains("GAME_CREATE_RESPONSE"))
    }
}
