package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.codec.NetworkMessageSerializer
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
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
        val header = MessageHeader(MessageType.CONNECTION_REQUEST)

        val bytes = NetworkMessageSerializer.serializeHeader(header)
        val result = NetworkMessageSerializer.deserializeHeader(bytes)

        assertEquals(header, result)
    }

    @Test
    fun `should deserialize create lobby request payload for create request type`() {
        val payload = CreateLobbyRequest
        val bytes =
            NetworkMessageSerializer.serializePayload(CreateLobbyRequest.serializer(), payload)

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_CREATE_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize create lobby error payload for error type`() {
        val payload = CreateLobbyErrorResponse(reason = "Lobby konnte nicht erstellt werden.")
        val bytes =
            NetworkMessageSerializer.serializePayload(
                CreateLobbyErrorResponse.serializer(),
                payload,
            )

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_CREATE_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize join lobby request payload for join request type`() {
        val payload =
            JoinLobbyRequest(
                lobbyCode = LobbyCode("AB12"),
                playerDisplayName = "Alice",
            )
        val bytes =
            NetworkMessageSerializer.serializePayload(JoinLobbyRequest.serializer(), payload)

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_JOIN_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize join lobby error payload for error type`() {
        val payload = JoinLobbyErrorResponse(reason = "Lobby wurde nicht gefunden.")
        val bytes =
            NetworkMessageSerializer.serializePayload(JoinLobbyErrorResponse.serializer(), payload)

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_JOIN_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered payload by runtime type`() {
        val payload =
            JoinLobbyRequest(
                lobbyCode = LobbyCode("AB12"),
                playerDisplayName = "Alice",
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_JOIN_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered connection response by runtime type`() {
        val payload = ConnectionResponse(SessionToken("123e4567-e89b-12d3-a456-426614174102"))

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.CONNECTION_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered join lobby response by runtime type`() {
        val payload =
            JoinLobbyResponse(
                lobbyCode = LobbyCode("CD34"),
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_JOIN_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered join lobby error by runtime type`() {
        val payload = JoinLobbyErrorResponse(reason = "Lobby wurde nicht gefunden.")

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_JOIN_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered create lobby response by runtime type`() {
        val payload =
            CreateLobbyResponse(
                lobbyCode = LobbyCode("CD34"),
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_CREATE_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered create lobby error by runtime type`() {
        val payload = CreateLobbyErrorResponse(reason = "Lobby konnte nicht erstellt werden.")

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_CREATE_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered player joined broadcast by runtime type`() {
        val payload =
            PlayerJoinedLobbyEvent(
                lobbyCode = LobbyCode("EF56"),
                playerId = PlayerId(7),
                playerDisplayName = "Bob",
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_PLAYER_JOINED_BROADCAST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize leave lobby request payload for leave request type`() {
        val payload = LeaveLobbyRequest(lobbyCode = LobbyCode("GH78"))
        val bytes =
            NetworkMessageSerializer.serializePayload(LeaveLobbyRequest.serializer(), payload)

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_LEAVE_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered leave lobby response by runtime type`() {
        val payload = LeaveLobbyResponse(lobbyCode = LobbyCode("IJ90"))

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_LEAVE_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered player left broadcast by runtime type`() {
        val payload = PlayerLeftLobbyEvent(lobbyCode = LobbyCode("KL12"), playerId = PlayerId(8))

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_PLAYER_LEFT_BROADCAST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should throw for unsupported payload type`() {
        val payloadBytes =
            """
            {"lobbyCode":"AB12","playerDisplayName":"Alice"}
            """.trimIndent().encodeToByteArray()

        val exception =
            assertThrows(UnsupportedPayloadTypeException::class.java) {
                NetworkMessageSerializer.deserializePayload(
                    MessageType.HEARTBEAT,
                    payloadBytes,
                )
            }

        assertEquals("Unsupported payload type: HEARTBEAT", exception.message)
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
                    MessageType.LOBBY_JOIN_REQUEST,
                    """{"lobbyCode":123,"playerDisplayName":true}""".encodeToByteArray(),
                )
            }

        assertEquals(
            "Failed to deserialize payload for message type LOBBY_JOIN_REQUEST",
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
