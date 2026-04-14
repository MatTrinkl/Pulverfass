package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.network.exception.NetworkSerializationException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadTypeException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkMessageSerializerTest {
    @Test
    fun `should serialize and deserialize header`() {
        val header = MessageHeader(MessageType.LOGIN_REQUEST)

        val bytes = NetworkMessageSerializer.serializeHeader(header)
        val result = NetworkMessageSerializer.deserializeHeader(bytes)

        assertEquals(header, result)
    }

    @Test
    fun `should deserialize login request payload for login request type`() {
        val payload = LoginRequest(username = "alice", password = "secret")
        val bytes = NetworkMessageSerializer.serializePayload(LoginRequest.serializer(), payload)

        val result = NetworkMessageSerializer.deserializePayload(MessageType.LOGIN_REQUEST, bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered payload by runtime type`() {
        val payload = GameJoinRequest(lobbyCode = at.aau.pulverfass.shared.ids.LobbyCode("AB12"))

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.GAME_JOIN_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should throw for unsupported payload type`() {
        val payloadBytes = """{"username":"alice","password":"secret"}""".encodeToByteArray()

        val exception =
            assertThrows(UnsupportedPayloadTypeException::class.java) {
                NetworkMessageSerializer.deserializePayload(
                    MessageType.LOGIN_RESPONSE,
                    payloadBytes,
                )
            }

        assertEquals("Unsupported payload type: LOGIN_RESPONSE", exception.message)
    }

    @Test
    fun `should wrap invalid header deserialization in network serialization exception`() {
        val exception =
            assertThrows(NetworkSerializationException::class.java) {
                NetworkMessageSerializer.deserializeHeader(
                    """{"type":"NOT_REAL"}""".encodeToByteArray(),
                )
            }

        assertEquals("Failed to deserialize message header", exception.message)
        assertTrue(exception.cause != null)
    }

    @Test
    fun `should throw for unsupported payload class`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                NetworkMessageSerializer.serializePayload(UnsupportedPayload)
            }

        assertEquals(
            "Unsupported payload class: ${UnsupportedPayload::class.java.name}",
            exception.message,
        )
    }

    @Test
    fun `should wrap serializer failures in network serialization exception`() {
        val exception =
            assertThrows(NetworkSerializationException::class.java) {
                NetworkMessageSerializer.serializePayload(
                    FailingPayloadSerializer,
                    FailingPayload,
                )
            }

        assertEquals(
            "Failed to serialize payload of type ${FailingPayload::class.java.name}",
            exception.message,
        )
        assertTrue(exception.cause is SerializationException)
    }

    @Test
    fun `should wrap invalid payload deserialization in network serialization exception`() {
        val exception =
            assertThrows(NetworkSerializationException::class.java) {
                NetworkMessageSerializer.deserializePayload(
                    MessageType.LOGIN_REQUEST,
                    """{"username":123,"password":true}""".encodeToByteArray(),
                )
            }

        assertEquals(
            "Failed to deserialize payload for message type LOGIN_REQUEST",
            exception.message,
        )
        assertTrue(exception.cause != null)
    }

    private data object UnsupportedPayload : NetworkMessagePayload

    private data object FailingPayload : NetworkMessagePayload

    private object FailingPayloadSerializer : KSerializer<FailingPayload> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("FailingPayload", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: FailingPayload,
        ) {
            throw SerializationException("boom")
        }

        override fun deserialize(decoder: Decoder): FailingPayload {
            throw SerializationException("boom")
        }
    }
}
