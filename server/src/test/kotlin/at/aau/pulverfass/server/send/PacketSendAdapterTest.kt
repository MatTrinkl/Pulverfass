package at.aau.pulverfass.server.send

import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.message.NetworkMessageSerializer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PacketSendAdapterTest {
    @Test
    fun `unknown connection leads to clear error`() =
        runBlocking {
            val adapter = PacketSendAdapter(ServerWebSocketTransport())
            val packet =
                SerializedPacket(
                    headerBytes = NetworkMessageSerializer.serializeHeader(MessageHeader(MessageType.HEARTBEAT)),
                    payloadBytes = byteArrayOf(1),
                )

            val exception =
                assertFailsWith<IllegalArgumentException> {
                    adapter.send(ConnectionId(99), packet)
                }

            assertEquals("Unknown connection: ConnectionId(value=99)", exception.message)
        }

    @Test
    fun `createPacket serializes header and preserves payload bytes`() {
        val adapter = PacketSendAdapter(ServerWebSocketTransport())
        val payload = byteArrayOf(7, 8)

        val packet = adapter.createPacket(MessageHeader(MessageType.LOGIN_REQUEST), payload)
        payload[0] = 0

        assertEquals(MessageType.LOGIN_REQUEST, NetworkMessageSerializer.deserializeHeader(packet.headerBytes).type)
        assertContentEquals(byteArrayOf(7, 8), packet.payloadBytes)
    }
}
