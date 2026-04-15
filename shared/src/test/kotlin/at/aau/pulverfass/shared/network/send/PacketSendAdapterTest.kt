package at.aau.pulverfass.shared.network.send

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.message.codec.NetworkMessageSerializer
import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.network.codec.PacketCodec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class PacketSendAdapterTest {
    @Test
    fun `send packs serialized packet into wire bytes`() =
        runSuspend {
            var capturedConnectionId: ConnectionId? = null
            var capturedBytes: ByteArray? = null
            val adapter =
                PacketSendAdapter { connectionId, bytes ->
                    capturedConnectionId = connectionId
                    capturedBytes = bytes
                }
            val packet =
                adapter.createPacket(
                    MessageHeader(MessageType.HEARTBEAT),
                    byteArrayOf(1, 2, 3),
                )

            adapter.send(ConnectionId(99), packet)

            assertEquals(ConnectionId(99), capturedConnectionId)
            assertArrayEquals(PacketCodec.pack(packet), capturedBytes)
        }

    @Test
    fun `createPacket serializes header and preserves payload bytes`() {
        val adapter = PacketSendAdapter { _, _ -> }
        val payload = byteArrayOf(7, 8)

        val packet = adapter.createPacket(MessageHeader(MessageType.CONNECTION_REQUEST), payload)
        payload[0] = 0

        assertEquals(
            MessageType.CONNECTION_REQUEST,
            NetworkMessageSerializer.deserializeHeader(packet.headerBytes).type,
        )
        assertArrayEquals(byteArrayOf(7, 8), packet.payloadBytes)
    }

    @Test
    fun `send with header and payload bytes creates and sends packed packet`() =
        runSuspend {
            var capturedBytes: ByteArray? = null
            val adapter =
                PacketSendAdapter { _, bytes ->
                    capturedBytes = bytes
                }

            adapter.send(
                connectionId = ConnectionId(5),
                header = MessageHeader(MessageType.CONNECTION_REQUEST),
                payloadBytes = byteArrayOf(9, 1),
            )

            val packet = PacketCodec.unpack(checkNotNull(capturedBytes))
            assertEquals(
                MessageType.CONNECTION_REQUEST,
                NetworkMessageSerializer.deserializeHeader(packet.headerBytes).type,
            )
            assertArrayEquals(byteArrayOf(9, 1), packet.payloadBytes)
        }

    private fun runSuspend(block: suspend () -> Unit) {
        var failure: Throwable? = null

        block.startCoroutine(
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    failure = result.exceptionOrNull()
                }
            },
        )

        failure?.let { throw it }
    }
}
