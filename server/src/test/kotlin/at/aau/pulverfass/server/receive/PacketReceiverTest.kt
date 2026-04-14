package at.aau.pulverfass.server.receive

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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketReceiverTest {
    @Test
    fun `should decode valid binary transport event to received packet`() =
        runBlocking {
            val receiver = PacketReceiver()
            val connectionId = ConnectionId(1)
            val payload = byteArrayOf(1, 2, 3)
            val bytes = encodePacket(MessageHeader(MessageType.LOGIN_REQUEST), payload)

            coroutineScope {
                val packetDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            receiver.packets.first()
                        }
                    }

                val result = receiver.decode(connectionId, bytes)
                val emittedPacket = packetDeferred.await()

                assertNotNull(result)
                assertEquals(result, emittedPacket)
                assertEquals(connectionId, result!!.connectionId)
                assertEquals(MessageType.LOGIN_REQUEST, result.header.type)
                assertArrayEquals(payload, result.packet.payloadBytes)
            }
        }

    @Test
    fun `should emit receive error for malformed binary transport event`() =
        runBlocking {
            val receiver = PacketReceiver()
            val connectionId = ConnectionId(2)
            val bytes = byteArrayOf(1, 2, 3)

            coroutineScope {
                val errorDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            receiver.errors.first()
                        }
                    }

                val result = receiver.decode(connectionId, bytes)
                val error = errorDeferred.await()

                assertNull(result)
                assertEquals(connectionId, error.connectionId)
                assertTrue(error.message!!.contains("Failed to decode received packet"))
            }
        }

    @Test
    fun `should decode binary transport event through onTransportEvent`() =
        runBlocking {
            val receiver = PacketReceiver()
            val connectionId = ConnectionId(3)
            val payload = byteArrayOf(9)
            val event =
                BinaryMessageReceived(
                    connectionId = connectionId,
                    bytes = encodePacket(MessageHeader(MessageType.HEARTBEAT), payload),
                )

            coroutineScope {
                val packetDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            receiver.packets.first()
                        }
                    }

                receiver.onTransportEvent(event)

                val packet = packetDeferred.await()
                assertEquals(MessageType.HEARTBEAT, packet.header.type)
                assertArrayEquals(payload, packet.packet.payloadBytes)
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
    fun `should preserve received packet as neutral model`() {
        val header = MessageHeader(MessageType.LOGIN_REQUEST)
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
                        payloadBytes = byteArrayOf(4, 5),
                    ),
            )

        assertEquals(ConnectionId(5), packet.connectionId)
        assertEquals(MessageType.LOGIN_REQUEST, packet.header.type)
        assertArrayEquals(byteArrayOf(4, 5), packet.packet.payloadBytes)
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
