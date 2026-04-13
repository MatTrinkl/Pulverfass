package at.aau.pulverfass.server

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.codec.MessageCodec
import at.aau.pulverfass.shared.network.message.LoginRequest
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ServerNetworkIntegrationTest {
    @Test
    fun `server network emits decoded payload for inbound binary frame`() =
        testApplication {
            val network = ServerNetwork()

            application {
                module(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val payload = LoginRequest(username = "alice", password = "secret")

            coroutineScope {
                val receivedDeferred =
                    async {
                        withTimeout(5_000) {
                            network.events
                                .filterIsInstance<Network.Event.MessageReceived<ConnectionId>>()
                                .first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                session.send(Frame.Binary(fin = true, data = MessageCodec.encode(payload)))

                val event = receivedDeferred.await()
                assertEquals(payload, event.payload)

                session.close()
            }
        }

    @Test
    fun `server network send wraps payload into binary frame for connected client`() =
        testApplication {
            val network = ServerNetwork()

            application {
                module(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val payload = LoginRequest(username = "bob", password = "topsecret")

            coroutineScope {
                val connectedDeferred =
                    async {
                        withTimeout(5_000) {
                            network.events
                                .filterIsInstance<Network.Event.Connected<ConnectionId>>()
                                .first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                val connected = connectedDeferred.await()

                network.send(connected.connectionId, payload)

                val frame =
                    withTimeout(5_000) {
                        session.incoming.receive()
                    }

                val binaryFrame = assertIs<Frame.Binary>(frame)
                assertEquals(payload, MessageCodec.decodePayload(binaryFrame.readBytes()))

                session.close()
            }
        }

    @Test
    fun `server network createServer injects usable high level network`() {
        val network = ServerNetwork()
        val server = createServer(host = "127.0.0.1", port = 0, network = network)

        try {
            server.start(wait = false)
            assertNotNull(network.events)
            assertNotNull(network)
        } finally {
            server.stop(1_000, 1_000)
        }
    }
}
