package at.aau.pulverfass.server.send

import at.aau.pulverfass.server.module
import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.network.codec.PacketCodec
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.message.NetworkMessageSerializer
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
import kotlin.test.assertTrue

class PacketSendAdapterIntegrationTest {
    @Test
    fun `server sends serialized packet as binary frame to connected client`() =
        testApplication {
            val transport = ServerWebSocketTransport()
            val adapter = PacketSendAdapter(transport)

            application {
                module(transport)
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val packet =
                SerializedPacket(
                    headerBytes = NetworkMessageSerializer.serializeHeader(MessageHeader(MessageType.HEARTBEAT)),
                    payloadBytes = byteArrayOf(1, 2, 3),
                )

            coroutineScope {
                val connectedEvent =
                    async {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<Connected>().first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                val connected = connectedEvent.await()

                adapter.send(connected.connectionId, packet)

                val frame =
                    withTimeout(5_000) {
                        session.incoming.receive()
                    }

                assertTrue(frame is Frame.Binary)
                assertContentEquals(PacketCodec.pack(packet), frame.readBytes())

                session.close()
            }
        }

    @Test
    fun `client receives valid packed packet with readable header`() =
        testApplication {
            val transport = ServerWebSocketTransport()
            val adapter = PacketSendAdapter(transport)

            application {
                module(transport)
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val payload = byteArrayOf(9, 8)

            coroutineScope {
                val connectedEvent =
                    async {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<Connected>().first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                val connected = connectedEvent.await()

                adapter.send(
                    connected.connectionId,
                    MessageHeader(MessageType.LOGIN_REQUEST),
                    payload,
                )

                val frame =
                    withTimeout(5_000) {
                        session.incoming.receive()
                    }

                require(frame is Frame.Binary)
                val packet = PacketCodec.unpack(frame.readBytes())
                val header = NetworkMessageSerializer.deserializeHeader(packet.headerBytes)

                assertEquals(MessageType.LOGIN_REQUEST, header.type)
                assertContentEquals(payload, packet.payloadBytes)

                session.close()
            }
        }
}
