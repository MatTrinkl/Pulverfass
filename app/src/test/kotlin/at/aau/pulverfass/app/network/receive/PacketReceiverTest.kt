package at.aau.pulverfass.app.network.receive

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import at.aau.pulverfass.shared.network.transport.BinaryMessageReceived
import at.aau.pulverfass.shared.network.transport.Connected
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacketReceiverTest {
    @Test
    fun `should decode valid binary transport event to received packet`() =
        runBlocking {
            val receiver = PacketReceiver()
            val connectionId = ConnectionId(1)
            val payload = byteArrayOf(1, 2, 3)
            val event =
                BinaryMessageReceived(
                    connectionId = connectionId,
                    bytes = encodePacket(MessageHeader(MessageType.LOGIN_REQUEST), payload),
                )

            coroutineScope {
                val packetDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            receiver.packets.first()
                        }
                    }

                receiver.onTransportEvent(event)
                val emittedPacket: ReceivedPacket = packetDeferred.await()

                assertEquals(connectionId, emittedPacket.connectionId)
                assertEquals(MessageType.LOGIN_REQUEST, emittedPacket.header.type)
                assertContentEquals(payload, emittedPacket.packet.payloadBytes)
            }
        }

    @Test
    fun `should emit receive error for malformed binary transport event`() =
        runBlocking {
            val receiver = PacketReceiver()
            val connectionId = ConnectionId(2)
            val event = BinaryMessageReceived(connectionId, byteArrayOf(1, 2, 3))

            coroutineScope {
                val errorDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            receiver.errors.first()
                        }
                    }

                receiver.onTransportEvent(event)
                val error = errorDeferred.await()

                assertEquals(connectionId, error.connectionId)
                assertTrue(error.message!!.contains("Failed to decode received packet"))
            }
        }

    @Test
    fun `should ignore non binary transport events`() =
        runBlocking {
            val receiver = PacketReceiver()
            val connectionId = ConnectionId(4)

            coroutineScope {
                val packetDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(250) {
                            receiver.packets.first()
                        }
                    }
                val errorDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(250) {
                            receiver.errors.first()
                        }
                    }

                receiver.onTransportEvent(Connected(connectionId))

                assertNull(runCatching { packetDeferred.await() }.getOrNull())
                assertNull(runCatching { errorDeferred.await() }.getOrNull())
            }
        }

    @Test
    fun `received packet model should preserve transport neutral data`() {
        val header = MessageHeader(MessageType.HEARTBEAT)
        val packet =
            ReceivedPacket(
                connectionId = ConnectionId(5),
                header = header,
                packet =
                    at.aau.pulverfass.shared.network.codec.SerializedPacket(
                        headerBytes =
                            Json
                                .encodeToString(MessageHeader.serializer(), header)
                                .encodeToByteArray(),
                        payloadBytes = byteArrayOf(9),
                    ),
            )

        assertNotNull(packet)
        assertEquals(ConnectionId(5), packet.connectionId)
        assertEquals(MessageType.HEARTBEAT, packet.header.type)
        assertContentEquals(byteArrayOf(9), packet.packet.payloadBytes)
    }

    private fun encodePacket(
        header: MessageHeader,
        payloadBytes: ByteArray,
    ): ByteArray {
        val headerBytes =
            Json
                .encodeToString(MessageHeader.serializer(), header)
                .encodeToByteArray()
        val buffer =
            ByteBuffer.allocate(Int.SIZE_BYTES + headerBytes.size + payloadBytes.size)
                .order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(headerBytes.size)
        buffer.put(headerBytes)
        buffer.put(payloadBytes)
        return buffer.array()
    }
}
