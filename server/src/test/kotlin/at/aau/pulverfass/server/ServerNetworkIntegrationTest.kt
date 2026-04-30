package at.aau.pulverfass.server

import at.aau.pulverfass.server.session.SessionManager
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectErrorCode
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
            val response = receiveConnectionResponse(session)

            assertNotNull(network.sessionManager.getByToken(response.sessionToken))

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
                discardConnectionHandshake(session)
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

                assertEquals(payload, receivePayload(session))
                session.close()
            }
        }

    @Test
    fun `server reconnects disconnected session by session token`() =
        testApplication {
            var now = 1_000L
            val network =
                ServerNetwork(
                    sessionManager =
                        SessionManager(
                            sessionTtlMillis = 5_000L,
                            nowEpochMillis = { now },
                        ),
                )

            application {
                module(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            coroutineScope {
                val firstSession = client.webSocketSession("/ws")
                val initialResponse = receiveConnectionResponse(firstSession)
                val initialConnectionId = awaitConnectionId(network, initialResponse.sessionToken)

                firstSession.close()
                awaitDetachedSession(network, initialResponse.sessionToken)

                val reconnectingSession = client.webSocketSession("/ws")
                discardConnectionHandshake(reconnectingSession)
                reconnectingSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(initialResponse.sessionToken)),
                    ),
                )

                val reconnectResponse =
                    assertIs<ReconnectResponse>(
                        receivePayload(reconnectingSession),
                    )
                val reboundConnectionId = awaitConnectionId(network, initialResponse.sessionToken)

                assertEquals(true, reconnectResponse.success)
                assertNull(reconnectResponse.errorCode)
                assertNotEquals(initialConnectionId, reboundConnectionId)

                reconnectingSession.close()
            }
        }

    @Test
    fun `server rejects reconnect with invalid session token`() =
        testApplication {
            val network = ServerNetwork()

            application {
                module(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            coroutineScope {
                val session = client.webSocketSession("/ws")
                discardConnectionHandshake(session)
                session.send(
                    Frame.Binary(
                        fin = true,
                        data =
                            MessageCodec.encode(
                                ReconnectRequest(
                                    SessionToken("123e4567-e89b-12d3-a456-426614174200"),
                                ),
                            ),
                    ),
                )

                val response = assertIs<ReconnectResponse>(receivePayload(session))

                assertEquals(
                    ReconnectResponse(
                        success = false,
                        errorCode = ReconnectErrorCode.TOKEN_INVALID,
                    ),
                    response,
                )

                session.close()
            }
        }

    @Test
    fun `server rejects reconnect after token ttl expired`() =
        testApplication {
            var now = 2_000L
            val network =
                ServerNetwork(
                    sessionManager =
                        SessionManager(
                            sessionTtlMillis = 100L,
                            nowEpochMillis = { now },
                        ),
                )

            application {
                module(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            coroutineScope {
                val firstSession = client.webSocketSession("/ws")
                val initialResponse = receiveConnectionResponse(firstSession)
                firstSession.close()
                awaitDetachedSession(network, initialResponse.sessionToken)
                now = 2_100L

                val reconnectingSession = client.webSocketSession("/ws")
                discardConnectionHandshake(reconnectingSession)
                reconnectingSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(initialResponse.sessionToken)),
                    ),
                )

                val response = assertIs<ReconnectResponse>(receivePayload(reconnectingSession))

                assertEquals(
                    ReconnectResponse(
                        success = false,
                        errorCode = ReconnectErrorCode.TOKEN_EXPIRED,
                    ),
                    response,
                )

                reconnectingSession.close()
            }
        }

    @Test
    fun `server rejects reconnect for revoked token`() =
        testApplication {
            val network = ServerNetwork()

            application {
                module(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            coroutineScope {
                val firstSession = client.webSocketSession("/ws")
                val initialResponse = receiveConnectionResponse(firstSession)
                firstSession.close()
                awaitDetachedSession(network, initialResponse.sessionToken)
                network.sessionManager.invalidate(initialResponse.sessionToken)

                val reconnectingSession = client.webSocketSession("/ws")
                discardConnectionHandshake(reconnectingSession)
                reconnectingSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(initialResponse.sessionToken)),
                    ),
                )

                val response = assertIs<ReconnectResponse>(receivePayload(reconnectingSession))

                assertEquals(
                    ReconnectResponse(
                        success = false,
                        errorCode = ReconnectErrorCode.TOKEN_REVOKED,
                    ),
                    response,
                )

                reconnectingSession.close()
            }
        }

    @Test
    fun `server closes old connection when reconnect replaces active binding`() =
        testApplication {
            val network = ServerNetwork()

            application {
                module(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            coroutineScope {
                val originalSession = client.webSocketSession("/ws")
                val initialResponse = receiveConnectionResponse(originalSession)
                val initialConnectionId = awaitConnectionId(network, initialResponse.sessionToken)

                val reconnectingSession = client.webSocketSession("/ws")
                discardConnectionHandshake(reconnectingSession)
                reconnectingSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(initialResponse.sessionToken)),
                    ),
                )

                val reconnectResponse =
                    assertIs<ReconnectResponse>(
                        receivePayload(reconnectingSession),
                    )
                val reboundConnectionId = awaitConnectionId(network, initialResponse.sessionToken)
                val closeReason =
                    withTimeout(5_000) {
                        originalSession.closeReason.await()
                    }

                assertEquals(true, reconnectResponse.success)
                assertNull(reconnectResponse.errorCode)
                assertNotEquals(initialConnectionId, reboundConnectionId)
                assertEquals(
                    CloseReason(
                        CloseReason.Codes.NORMAL,
                        "Connection replaced by reconnect.",
                    ),
                    closeReason,
                )

                reconnectingSession.close()
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

    private suspend fun discardConnectionHandshake(session: DefaultClientWebSocketSession) {
        receiveConnectionResponse(session)
    }

    private suspend fun receiveConnectionResponse(
        session: DefaultClientWebSocketSession,
    ): ConnectionResponse = assertIs(receivePayload(session))

    private suspend fun receivePayload(
        session: DefaultClientWebSocketSession,
    ): NetworkMessagePayload {
        val frame =
            withTimeout(5_000) {
                session.incoming.receive()
            }

        val binaryFrame = assertIs<Frame.Binary>(frame)
        return MessageCodec.decodePayload(binaryFrame.readBytes())
    }

    private suspend fun awaitConnectionId(
        network: ServerNetwork,
        sessionToken: SessionToken,
    ): ConnectionId =
        withTimeout(5_000) {
            var connectionId: ConnectionId? = null
            while (connectionId == null) {
                connectionId = network.sessionManager.getByToken(sessionToken)?.connectionId
                if (connectionId == null) {
                    delay(5)
                }
            }
            connectionId
        }

    private suspend fun awaitDetachedSession(
        network: ServerNetwork,
        sessionToken: SessionToken,
    ) {
        withTimeout(5_000) {
            while (network.sessionManager.getByToken(sessionToken)?.connectionId != null) {
                delay(5)
            }
        }
    }
}
