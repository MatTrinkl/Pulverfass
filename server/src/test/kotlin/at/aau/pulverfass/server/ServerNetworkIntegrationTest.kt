package at.aau.pulverfass.server

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerNetworkIntegrationTest {
    @Test
    fun `server sends session token immediately after websocket connect`() =
        testApplication {
            val network = ServerNetwork()

            application {
                module(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            val session = client.webSocketSession("/ws")
            val frame =
                withTimeout(5_000) {
                    session.incoming.receive()
                }

            val binaryFrame = assertIs<Frame.Binary>(frame)
            val payload =
                assertIs<ConnectionResponse>(
                    MessageCodec.decodePayload(binaryFrame.readBytes()),
                )
            assertNotNull(network.sessionManager.getByToken(payload.sessionToken))

            session.close()
        }

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
            val payload = JoinLobbyRequest(LobbyCode("AB12"), "alice")

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
            val payload = JoinLobbyRequest(LobbyCode("CD34"), "bob")

            coroutineScope {
                val connectedDeferred =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            network.events
                                .filterIsInstance<Network.Event.Connected<ConnectionId>>()
                                .first()
                        }
                    }

                val session = client.webSocketSession("/ws")
                val connected = connectedDeferred.await()
                discardConnectionHandshake(session)

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

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T)
        return value as T
    }

    private suspend fun discardConnectionHandshake(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ) {
        val frame =
            withTimeout(5_000) {
                session.incoming.receive()
            }
        val binaryFrame = assertIs<Frame.Binary>(frame)
        val payload = MessageCodec.decodePayload(binaryFrame.readBytes())
        assertTrue(payload is ConnectionResponse)
    }
}
