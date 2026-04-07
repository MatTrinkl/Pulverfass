package at.aau.pulverfass.server

import at.aau.pulverfass.shared.network.codec.PacketCodec
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.message.NetworkMessageSerializer
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import at.aau.pulverfass.shared.network.transport.Connected
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerNetworkIntegrationTest {
    @Test
    fun `server network forwards inbound binary frame to packet receiver`() =
        testApplication {
            val network = ServerNetwork()

            application {
                module(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val payload = byteArrayOf(10, 11)
            val sentPacket =
                SerializedPacket(
                    headerBytes = NetworkMessageSerializer.serializeHeader(MessageHeader(MessageType.HEARTBEAT)),
                    payloadBytes = payload,
                )

            coroutineScope {
                val decodedPacketDeferred =
                    async {
                        withTimeout(5_000) {
                            network.packetReceiver.packets.first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                session.send(Frame.Binary(fin = true, data = PacketCodec.pack(sentPacket)))

                val decodedPacket = decodedPacketDeferred.await()
                assertEquals(MessageType.HEARTBEAT, decodedPacket.header.type)
                assertContentEquals(payload, decodedPacket.packet.payloadBytes)

                session.close()
            }
        }

    @Test
    fun `server network packet sender sends packed packet to connected client`() =
        testApplication {
            val network = ServerNetwork()

            application {
                module(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val packet =
                SerializedPacket(
                    headerBytes = NetworkMessageSerializer.serializeHeader(MessageHeader(MessageType.LOGIN_REQUEST)),
                    payloadBytes = byteArrayOf(1, 2, 3, 4),
                )

            coroutineScope {
                val connectedDeferred =
                    async {
                        withTimeout(5_000) {
                            network.transport.events.filterIsInstance<Connected>().first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                val connected = connectedDeferred.await()

                network.packetSender.send(connected.connectionId, packet)

                val frame =
                    withTimeout(5_000) {
                        session.incoming.receive()
                    }

                assertTrue(frame is Frame.Binary)

                val receivedPacket = PacketCodec.unpack(frame.readBytes())
                val header = NetworkMessageSerializer.deserializeHeader(receivedPacket.headerBytes)

                assertEquals(MessageType.LOGIN_REQUEST, header.type)
                assertContentEquals(packet.payloadBytes, receivedPacket.payloadBytes)

                session.close()
            }
        }

    @Test
    fun `server network createServer injects usable packet sender and receiver`() {
        val network = ServerNetwork()
        val server = createServer(host = "127.0.0.1", port = 0, network = network)

        try {
            server.start(wait = false)
            assertNotNull(network.packetSender)
            assertNotNull(network.packetReceiver.packets)
        } finally {
            server.stop(1_000, 1_000)
        }
    }
}
