package at.aau.pulverfass.server.lobby.mapping

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DecodedNetworkRequestTest {
    @Test
    fun `should expose packet header and connection accessors`() {
        val packet =
            ReceivedPacket(
                connectionId = ConnectionId(1),
                header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                packet = SerializedPacket(byteArrayOf(1), byteArrayOf(2)),
            )
        val request =
            DecodedNetworkRequest(
                receivedPacket = packet,
                payload = JoinLobbyRequest(LobbyCode("AB12"), "Alice"),
                context = EventContext(connectionId = ConnectionId(1), occurredAtEpochMillis = 123),
            )

        assertSame(packet, request.receivedPacket)
        assertEquals(ConnectionId(1), request.connectionId)
        assertEquals(MessageType.LOBBY_JOIN_REQUEST, request.header.type)
    }

    @Test
    fun `should reject mismatching context connection id`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                DecodedNetworkRequest(
                    receivedPacket =
                        ReceivedPacket(
                            connectionId = ConnectionId(1),
                            header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                            packet = SerializedPacket(byteArrayOf(1), byteArrayOf()),
                        ),
                    payload = JoinLobbyRequest(LobbyCode("AB12"), "Alice"),
                    context =
                        EventContext(
                            connectionId = ConnectionId(2),
                            occurredAtEpochMillis = 456,
                        ),
                )
            }

        assertEquals(
            "EventContext.connectionId muss null oder gleich request.connectionId sein.",
            exception.message,
        )
    }

    @Test
    fun `should allow missing context connection id`() {
        val request =
            DecodedNetworkRequest(
                receivedPacket =
                    ReceivedPacket(
                        connectionId = ConnectionId(7),
                        header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                        packet = SerializedPacket(byteArrayOf(1), byteArrayOf()),
                    ),
                payload = JoinLobbyRequest(LobbyCode("CD34"), "Bob"),
                context = EventContext(occurredAtEpochMillis = 789),
            )

        assertEquals(ConnectionId(7), request.connectionId)
    }
}
