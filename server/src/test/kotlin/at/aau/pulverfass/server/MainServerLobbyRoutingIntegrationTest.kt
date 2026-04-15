package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MainServerLobbyRoutingIntegrationTest {
    @Test
    fun `server websocket packets werden ueber router in mehrere lobbys korrekt verteilt`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router =
                MainServerRouter(
                    lobbyManager = lobbyManager,
                    mapper = DefaultNetworkToLobbyEventMapper(),
                )
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val routedPackets = AtomicInteger(0)
            val routingErrors = AtomicInteger(0)
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
                    hooks =
                        MainServerLobbyRoutingServiceHooks(
                            onRouted = { routedPackets.incrementAndGet() },
                            onRoutingError = { _, _ -> routingErrors.incrementAndGet() },
                        ),
                )

            application {
                module(network)
            }

            val lobbyA = LobbyCode("AB12")
            val lobbyB = LobbyCode("CD34")
            lobbyManager.createLobby(lobbyA)
            lobbyManager.createLobby(lobbyB)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionA1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionA2AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(2),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionB1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(3),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionA1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Alice")),
                        ),
                    )
                    sessionA2AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Bob")),
                        ),
                    )
                    sessionB1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyB, "Carol")),
                        ),
                    )

                    waitUntilProcessed(lobbyManager, lobbyA, expectedCount = 2)
                    waitUntilProcessed(lobbyManager, lobbyB, expectedCount = 1)

                    val stateA = lobbyManager.getLobby(lobbyA)?.currentState()
                    val stateB = lobbyManager.getLobby(lobbyB)?.currentState()
                    assertEquals(2, stateA?.processedEventCount)
                    assertEquals(1, stateB?.processedEventCount)
                    assertEquals(setOf(PlayerId(1), PlayerId(2)), stateA?.players?.toSet())
                    assertEquals(setOf(PlayerId(3)), stateB?.players?.toSet())
                    assertEquals(3, routedPackets.get())
                    assertEquals(0, routingErrors.get())

                    sessionA1AndConnection.first.close()
                    sessionA2AndConnection.first.close()
                    sessionB1AndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `failed join sends join error response to requesting client`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router =
                MainServerRouter(
                    lobbyManager = lobbyManager,
                    mapper = DefaultNetworkToLobbyEventMapper(),
                )
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
                )

            application {
                module(network)
            }

            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionAndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    JoinLobbyRequest(LobbyCode("ZZ99"), "Alice"),
                                ),
                        ),
                    )

                    val payload = receivePayload(sessionAndConnection.first)

                    assertIs<JoinLobbyErrorResponse>(payload)
                    assertEquals(
                        JoinLobbyErrorResponse("Lobby 'ZZ99' wurde nicht gefunden."),
                        payload,
                    )

                    sessionAndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `successful join sends response and lobby scoped broadcast`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router =
                MainServerRouter(
                    lobbyManager = lobbyManager,
                    mapper = DefaultNetworkToLobbyEventMapper(),
                )
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
                )

            application {
                module(network)
            }

            val lobbyA = LobbyCode("AB12")
            val lobbyB = LobbyCode("CD34")
            lobbyManager.createLobby(lobbyA)
            lobbyManager.createLobby(lobbyB)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionA1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionA2AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(2),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionB1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(3),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionA1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Alice")),
                        ),
                    )
                    assertEquals(
                        JoinLobbyResponse(lobbyA),
                        receivePayload(sessionA1AndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(1), "Alice"),
                        receivePayload(sessionA1AndConnection.first),
                    )

                    sessionB1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyB, "Carol")),
                        ),
                    )
                    assertEquals(
                        JoinLobbyResponse(lobbyB),
                        receivePayload(sessionB1AndConnection.first),
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyB, PlayerId(3), "Carol"),
                        receivePayload(sessionB1AndConnection.first),
                    )

                    sessionA2AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Bob")),
                        ),
                    )

                    val joinerResponse = receivePayload(sessionA2AndConnection.first)
                    val joinerBroadcast = receivePayload(sessionA2AndConnection.first)
                    val memberBroadcast = receivePayload(sessionA1AndConnection.first)
                    val otherLobbyPayload = receivePayloadOrNull(sessionB1AndConnection.first)

                    assertIs<JoinLobbyResponse>(joinerResponse)
                    assertEquals(JoinLobbyResponse(lobbyA), joinerResponse)
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(2), "Bob"),
                        joinerBroadcast,
                    )
                    assertEquals(
                        PlayerJoinedLobbyEvent(lobbyA, PlayerId(2), "Bob"),
                        memberBroadcast,
                    )
                    assertNull(otherLobbyPayload)

                    sessionA1AndConnection.first.close()
                    sessionA2AndConnection.first.close()
                    sessionB1AndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `successful leave sends response and lobby scoped event only to remaining members`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router =
                MainServerRouter(
                    lobbyManager = lobbyManager,
                    mapper = DefaultNetworkToLobbyEventMapper(),
                )
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
                )

            application {
                module(network)
            }

            val lobbyA = LobbyCode("EF56")
            val lobbyB = LobbyCode("GH78")
            lobbyManager.createLobby(lobbyA)
            lobbyManager.createLobby(lobbyB)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionA1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionA2AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(2),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val sessionB1AndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(3),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionA1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Alice")),
                        ),
                    )
                    receivePayload(sessionA1AndConnection.first)
                    receivePayload(sessionA1AndConnection.first)

                    sessionA2AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyA, "Bob")),
                        ),
                    )
                    receivePayload(sessionA2AndConnection.first)
                    receivePayload(sessionA2AndConnection.first)
                    receivePayload(sessionA1AndConnection.first)

                    sessionB1AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(JoinLobbyRequest(lobbyB, "Carol")),
                        ),
                    )
                    receivePayload(sessionB1AndConnection.first)
                    receivePayload(sessionB1AndConnection.first)

                    sessionA2AndConnection.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(LeaveLobbyRequest(lobbyA)),
                        ),
                    )

                    waitUntilProcessed(lobbyManager, lobbyA, expectedCount = 3)

                    val leaverResponse = receivePayload(sessionA2AndConnection.first)
                    val remainingMemberEvent = receivePayload(sessionA1AndConnection.first)
                    val leaverScopedPayload = receivePayloadOrNull(sessionA2AndConnection.first)
                    val otherLobbyPayload = receivePayloadOrNull(sessionB1AndConnection.first)

                    assertEquals(LeaveLobbyResponse(lobbyA), leaverResponse)
                    assertEquals(PlayerLeftLobbyEvent(lobbyA, PlayerId(2)), remainingMemberEvent)
                    assertNull(leaverScopedPayload)
                    assertNull(otherLobbyPayload)
                    assertEquals(
                        listOf(PlayerId(1)),
                        lobbyManager.getLobby(lobbyA)?.currentState()?.players,
                    )

                    sessionA1AndConnection.first.close()
                    sessionA2AndConnection.first.close()
                    sessionB1AndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `create request creates lobby and returns create response to requesting client`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router =
                MainServerRouter(
                    lobbyManager = lobbyManager,
                    mapper = DefaultNetworkToLobbyEventMapper(),
                )
            val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
            val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
                )

            application {
                module(network)
            }

            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val sessionAndConnection =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = PlayerId(1),
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    sessionAndConnection.first.send(
                        Frame.Binary(fin = true, data = MessageCodec.encode(CreateLobbyRequest)),
                    )

                    val payload = receivePayload(sessionAndConnection.first)
                    val response = assertIs<CreateLobbyResponse>(payload)
                    assertEquals(4, response.lobbyCode.value.length)
                    assertEquals(
                        response.lobbyCode,
                        lobbyManager.getLobby(response.lobbyCode)?.lobbyCode,
                    )

                    sessionAndConnection.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private suspend fun connectSessionWithConnection(
        client: io.ktor.client.HttpClient,
        network: ServerNetwork,
        playerId: PlayerId,
        playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
        connectionsByPlayer: ConcurrentHashMap<PlayerId, ConnectionId>,
    ) = coroutineScope {
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
        playersByConnection[connected.connectionId] = playerId
        connectionsByPlayer[playerId] = connected.connectionId
        session to connected.connectionId
    }

    private suspend fun receivePayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): NetworkMessagePayload {
        val frame =
            withTimeout(5_000) {
                session.incoming.receive()
            }

        val binary = assertIs<Frame.Binary>(frame)
        return MessageCodec.decodePayload(binary.readBytes())
    }

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): NetworkMessagePayload? {
        val frame =
            withTimeoutOrNull(500) {
                session.incoming.receive()
            } ?: return null

        val binary = assertIs<Frame.Binary>(frame)
        return MessageCodec.decodePayload(binary.readBytes())
    }

    private suspend fun waitUntilProcessed(
        manager: LobbyManager,
        lobbyCode: LobbyCode,
        expectedCount: Long,
    ) {
        withTimeout(5_000) {
            while (
                (manager.getLobby(lobbyCode)?.currentState()?.processedEventCount ?: 0L) <
                expectedCount
            ) {
                delay(5)
            }
        }
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T)
        return value as T
    }
}
