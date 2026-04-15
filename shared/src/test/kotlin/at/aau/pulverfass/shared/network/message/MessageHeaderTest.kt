package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.message.protocol.MessageHeaderSerializer
import at.aau.pulverfass.shared.message.protocol.MessageType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class MessageHeaderTest {
    private val json = Json

    @Test
    fun `should create message header correctly`() {
        val header = MessageHeader(type = MessageType.CONNECTION_REQUEST)

        assertEquals(MessageType.CONNECTION_REQUEST, header.type)
    }

    @Test
    fun `should serialize message header with enum name`() {
        val header = MessageHeader(type = MessageType.CHAT_MESSAGE)

        val serialized = json.encodeToString(header)

        assertEquals("""{"type":"CHAT_MESSAGE"}""", serialized)
    }

    @Test
    fun `should deserialize message header with enum name`() {
        val jsonString = """{"type":"LOBBY_JOIN_REQUEST"}"""

        val deserialized = json.decodeFromString<MessageHeader>(jsonString)

        assertEquals(MessageHeader(MessageType.LOBBY_JOIN_REQUEST), deserialized)
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
        val header3 = MessageHeader(MessageType.CONNECTION_REQUEST)

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
        val header = MessageHeader(type = MessageType.LOBBY_CREATE_RESPONSE)

        val jsonString = json.encodeToString(header)

        assertTrue(jsonString.contains("type"))
        assertTrue(jsonString.contains("LOBBY_CREATE_RESPONSE"))
    }

    @Test
    fun `should reject null message type at constructor boundary`() {
        val constructor =
            MessageHeader::class.java.declaredConstructors.first { it.parameterCount == 1 }
        val valid = constructor.newInstance(MessageType.HEARTBEAT) as MessageHeader

        assertEquals(MessageType.HEARTBEAT, valid.type)

        assertThrows(InvocationTargetException::class.java) {
            constructor.newInstance(*arrayOf<Any?>(null))
        }
    }

    @Test
    fun `should reject missing message type during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<MessageHeader>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            MessageHeaderSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `should reject missing field in serializer directly`() {
        assertThrows(kotlinx.serialization.MissingFieldException::class.java) {
            MessageHeaderSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }
}
