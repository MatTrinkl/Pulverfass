package at.aau.pulverfass.server.transport

import at.aau.pulverfass.server.WebSocketPolicy
import at.aau.pulverfass.server.module
import at.aau.pulverfass.shared.network.transport.BinaryMessageReceived
import at.aau.pulverfass.shared.network.transport.Connected
import at.aau.pulverfass.shared.network.transport.Disconnected
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerWebSocketTransportIntegrationTest {
    @Test
    fun `connect event is emitted on new websocket connection`() =
        testApplication {
            val transport = ServerWebSocketTransport()

            application {
                module(transport)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            coroutineScope {
                val connectedEvent =
                    async {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<Connected>().first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                val event = connectedEvent.await()

                assertTrue(event.connectionId.value > 0)
                session.close()
            }
        }

    @Test
    fun `binary frame is emitted as raw byte array event`() =
        testApplication {
            val transport = ServerWebSocketTransport()

            application {
                module(transport)
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val payload = byteArrayOf(1, 2, 3, 4)

            coroutineScope {
                val messageEvent =
                    async {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<BinaryMessageReceived>().first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                session.send(Frame.Binary(fin = true, data = payload))

                val event = messageEvent.await()
                assertContentEquals(payload, event.bytes)

                session.close()
            }
        }

    @Test
    fun `binary frame keeps websocket connection open`() =
        testApplication {
            val transport = ServerWebSocketTransport()

            application {
                module(transport)
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val inboundPayload = byteArrayOf(1, 2, 3)
            val outboundPayload = byteArrayOf(4, 5, 6)

            coroutineScope {
                val connectedEvent =
                    async {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<Connected>().first()
                        }
                    }
                val messageEvent =
                    async {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<BinaryMessageReceived>().first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                val connected = connectedEvent.await()

                session.send(Frame.Binary(fin = true, data = inboundPayload))

                val message = messageEvent.await()
                assertContentEquals(inboundPayload, message.bytes)

                transport.send(connected.connectionId, outboundPayload)

                val frame =
                    withTimeout(5_000) {
                        session.incoming.receive()
                    }

                assertTrue(frame is Frame.Binary)
                assertContentEquals(outboundPayload, frame.readBytes())
                assertNull(
                    withTimeoutOrNull(250) {
                        session.closeReason.await()
                    },
                )

                session.close()
            }
        }

    @Test
    fun `disconnect event is emitted when websocket closes`() =
        testApplication {
            val transport = ServerWebSocketTransport()

            application {
                module(transport)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            coroutineScope {
                val connectedEvent =
                    async {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<Connected>().first()
                        }
                    }
                val disconnectedEvent =
                    async {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<Disconnected>().first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                val connected = connectedEvent.await()

                session.close()

                val disconnected = disconnectedEvent.await()
                assertEquals(connected.connectionId, disconnected.connectionId)
            }
        }

    @Test
    fun `send connection id bytes writes binary frame to websocket client`() =
        testApplication {
            val transport = ServerWebSocketTransport()

            application {
                module(transport)
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val payload = byteArrayOf(9, 8, 7)

            coroutineScope {
                val connectedEvent =
                    async {
                        withTimeout(5_000) {
                            transport.events.filterIsInstance<Connected>().first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                val connected = connectedEvent.await()

                transport.send(connected.connectionId, payload)

                val frame =
                    withTimeout(5_000) {
                        session.incoming.receive()
                    }

                assertTrue(frame is Frame.Binary)
                assertContentEquals(payload, frame.readBytes())

                session.close()
            }
        }

    @Test
    fun `text frame closes websocket with documented reason and no binary event`() =
        testApplication {
            val transport = ServerWebSocketTransport()

            application {
                module(transport)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            coroutineScope {
                val binaryEvent =
                    async {
                        withTimeoutOrNull(500) {
                            transport.events.filterIsInstance<BinaryMessageReceived>().first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                session.send(Frame.Text("not-supported"))

                val closeReason = assertNotNull(session.closeReason.await())
                assertEquals(
                    CloseReason.Codes.byCode(WebSocketPolicy.TEXT_FRAME_CLOSE_CODE),
                    closeReason.knownReason,
                )
                assertEquals(WebSocketPolicy.TEXT_FRAMES_NOT_SUPPORTED, closeReason.message)
                assertNull(binaryEvent.await())
            }
        }
}
