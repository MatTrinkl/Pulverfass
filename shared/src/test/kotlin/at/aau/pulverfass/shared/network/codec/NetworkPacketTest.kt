package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.exception.PayloadTypeMismatchException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NetworkPacketTest {
    private data class UnknownPayload(
        val value: String,
    ) : NetworkMessagePayload

    @Test
    fun `should create network packet correctly`() {
        val payload = JoinLobbyRequest(LobbyCode("AB12"), "alice")

        val packet =
            NetworkPacket(header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST), payload = payload)

        assertEquals(MessageType.LOBBY_JOIN_REQUEST, packet.header.type)
        assertEquals(payload, packet.payload)
    }

    @Test
    fun `should support equality for same header and payload`() {
        val first =
            NetworkPacket(
                header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                payload = JoinLobbyRequest(LobbyCode("AB12"), "alice"),
            )
        val second =
            NetworkPacket(
                header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                payload = JoinLobbyRequest(LobbyCode("AB12"), "alice"),
            )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `should detect different packets`() {
        val first =
            NetworkPacket(
                header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                payload = JoinLobbyRequest(LobbyCode("AB12"), "alice"),
            )
        val second =
            NetworkPacket(
                header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                payload = JoinLobbyRequest(LobbyCode("AB12"), "bob"),
            )

        assertNotEquals(first, second)
    }

    @Test
    fun `should reject packet when header type does not match payload type`() {
        val exception =
            assertThrows(PayloadTypeMismatchException::class.java) {
                NetworkPacket(
                    header = MessageHeader(MessageType.LOGOUT_REQUEST),
                    payload = JoinLobbyRequest(LobbyCode("AB12"), "alice"),
                )
            }

        assertEquals(
            "Header type LOGOUT_REQUEST does not match payload type LOBBY_JOIN_REQUEST",
            exception.message,
        )
    }

    @Test
    fun `should reject packet with unregistered payload class`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                NetworkPacket(
                    header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                    payload = UnknownPayload("test"),
                )
            }

        assertEquals(
            "Unsupported payload class: ${UnknownPayload::class.java.name}",
            exception.message,
        )
    }
}
