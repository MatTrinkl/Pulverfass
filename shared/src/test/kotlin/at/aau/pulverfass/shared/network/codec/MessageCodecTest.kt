package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
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
import at.aau.pulverfass.shared.network.exception.InvalidSerializedPacketException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageCodecTest {
    @Test
    fun `should encode and decode create lobby request payload directly`() {
        val payload = CreateLobbyRequest

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode create lobby error payload directly`() {
        val payload = CreateLobbyErrorResponse(reason = "Lobby konnte nicht erstellt werden.")

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode create lobby response payload directly`() {
        val payload =
            CreateLobbyResponse(
                lobbyCode = LobbyCode("AB12"),
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode join lobby request payload directly`() {
        val payload =
            JoinLobbyRequest(
                LobbyCode("AB12"),
                "Alice",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode join lobby response payload directly`() {
        val payload = JoinLobbyResponse(LobbyCode("CD34"))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode join lobby error payload directly`() {
        val payload = JoinLobbyErrorResponse(reason = "Lobby wurde nicht gefunden.")

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode join lobby request payload with display name directly`() {
        val payload =
            JoinLobbyRequest(
                LobbyCode("BC23"),
                "Bob",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode player joined lobby event payload directly`() {
        val payload =
            PlayerJoinedLobbyEvent(
                lobbyCode = LobbyCode("EF56"),
                playerId = PlayerId(7),
                playerDisplayName = "Carol",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode leave lobby request payload directly`() {
        val payload = LeaveLobbyRequest(LobbyCode("FG67"))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode leave lobby response payload directly`() {
        val payload = LeaveLobbyResponse(LobbyCode("GH78"))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode player left lobby event payload directly`() {
        val payload = PlayerLeftLobbyEvent(lobbyCode = LobbyCode("HI89"), playerId = PlayerId(6))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode join lobby request packet`() {
        val packet =
            NetworkPacket(
                header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                payload = JoinLobbyRequest(LobbyCode("CD34"), "Dora"),
            )

        val bytes = MessageCodec.encode(packet, JoinLobbyRequest.serializer())
        val result = MessageCodec.decode(bytes)

        assertEquals(packet.header, result.header)
        assertEquals(packet.payload, result.payload)
    }

    @Test
    fun `should decode payload directly from received packet without reframing`() {
        val payload = JoinLobbyRequest(LobbyCode("DE45"), "Eve")
        val encoded = MessageCodec.encode(payload)
        val unpacked = PacketCodec.unpack(encoded)
        val receivedPacket =
            ReceivedPacket(
                connectionId = at.aau.pulverfass.shared.ids.ConnectionId(1),
                header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                packet = unpacked,
            )

        val result = MessageCodec.decodePayload(receivedPacket)

        assertEquals(payload, result)
    }

    @Test
    fun `should wrap malformed frame as invalid serialized packet exception`() {
        val exception =
            assertThrows(InvalidSerializedPacketException::class.java) {
                MessageCodec.decodePayload(byteArrayOf(1, 2, 3))
            }

        assertTrue(exception.message!!.contains("Packet too short"))
    }

    @Test
    fun `should reject unsupported payload classes`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                MessageCodec.encode(UnsupportedPayload)
            }

        assertEquals(
            "Unsupported payload class: ${UnsupportedPayload::class.java.name}",
            exception.message,
        )
    }

    private data object UnsupportedPayload :
        NetworkMessagePayload
}
