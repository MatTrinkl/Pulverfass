package at.aau.pulverfass.app.network

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import at.aau.pulverfass.shared.network.transport.BinaryMessageReceived
import at.aau.pulverfass.shared.network.transport.Connected
import at.aau.pulverfass.shared.network.transport.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ClientNetworkTest {
    @Test
    fun `connect should propagate transport failure for invalid server url`() {
        runBlocking {
            val network = createNetwork()
            try {
                assertFailsWith<Exception> {
                    network.connect("not-a-valid-websocket-url")
                }
            } finally {
                network.close()
            }
        }
    }

    @Test
    fun `disconnect should be safe when no active connection exists`() {
        runBlocking {
            val network = createNetwork()
            try {
                network.disconnect("test disconnect")
            } finally {
                network.close()
            }
        }
    }

    @Test
    fun `send login request should fail without active websocket session`() {
        runBlocking {
            val network = createNetwork()
            try {
                val error =
                    assertFailsWith<IllegalStateException> {
                        network.sendLoginRequest(
                            username = "alice",
                            password = "pw",
                        )
                    }

                assertEquals("No active websocket session", error.message)
            } finally {
                network.close()
            }
        }
    }

    @Test
    fun `send json message should fail without active websocket session`() {
        runBlocking {
            val network = createNetwork()
            try {
                val error =
                    assertFailsWith<IllegalStateException> {
                        network.sendJsonMessage(
                            messageType = MessageType.GAME_CREATE_REQUEST,
                            payloadJson = """{"x":1}""",
                        )
                    }

                assertEquals("No active websocket session", error.message)
            } finally {
                network.close()
            }
        }
    }

    @Test
    fun `transport binary event should be forwarded to packet receiver`() {
        runBlocking {
            val network = createNetwork()
            try {
                val connectionId = ConnectionId(42)
                val payloadBytes = byteArrayOf(7, 8, 9)
                val event =
                    BinaryMessageReceived(
                        connectionId = connectionId,
                        bytes = encodePacket(MessageHeader(MessageType.HEARTBEAT), payloadBytes),
                    )

                coroutineScope {
                    val packetDeferred =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            withTimeout(5_000) {
                                network.packetReceiver.packets.first()
                            }
                        }

                    emitTransportEvent(network, event)

                    val packet: ReceivedPacket = packetDeferred.await()
                    assertEquals(connectionId, packet.connectionId)
                    assertEquals(MessageType.HEARTBEAT, packet.header.type)
                    assertEquals(3, packet.packet.payloadBytes.size)
                }
            } finally {
                network.close()
            }
        }
    }

    @Test
    fun `transport non-binary event should be ignored by packet receiver`() {
        runBlocking {
            val network = createNetwork()
            try {
                emitTransportEvent(
                    network,
                    Connected(ConnectionId(1)),
                )

                val packetOrNull =
                    runCatching {
                        withTimeout(200) {
                            network.packetReceiver.packets.first()
                        }
                    }.getOrNull()

                assertNull(packetOrNull)
            } finally {
                network.close()
            }
        }
    }

    @Test
    fun `close should release transport resources`() {
        val network = createNetwork()
        network.close()
    }

    private fun createNetwork(): ClientNetwork {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return ClientNetwork(scope = scope)
    }

    private fun emitTransportEvent(
        network: ClientNetwork,
        event: TransportEvent,
    ) {
        @Suppress("UNCHECKED_CAST")
        val events =
            readPrivateField(
                target = network.transport,
                fieldName = "_events",
            ) as MutableSharedFlow<TransportEvent>
        events.tryEmit(event)
    }

    private fun readPrivateField(
        target: Any,
        fieldName: String,
    ): Any? {
        val field =
            target::class.java.getDeclaredField(fieldName).apply {
                isAccessible = true
            }
        return field.get(target)
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
