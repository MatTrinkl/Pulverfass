package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LobbyPlayerCountRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LobbyPlayerCountResponse
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorResponse
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class LobbyPlayerCountIntegrationTest {
    @Test
    fun `player count request replies only to requesting client`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val routedPackets = AtomicInteger(0)
            val routingErrors = AtomicInteger(0)
            val router =
                MainServerRouter(
                    lobbyManager = lobbyManager,
                    mapper = DefaultNetworkToLobbyEventMapper(),
                )
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { null },
                    hooks =
                        MainServerLobbyRoutingServiceHooks(
                            onRouted = { routedPackets.incrementAndGet() },
                            onRoutingError = { _, _ -> routingErrors.incrementAndGet() },
                        ),
                )

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("PC01")
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    lobbyState(
                        lobbyCode = lobbyCode,
                        players = listOf(PlayerId(1), PlayerId(2), PlayerId(3)),
                    ),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val requester = connectSession(client, network)
                    val otherClient = connectSession(client, network)

                    requester.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(LobbyPlayerCountRequest(lobbyCode)),
                        ),
                    )

                    assertEquals(
                        LobbyPlayerCountResponse(
                            lobbyCode = lobbyCode,
                            playerCount = 3,
                        ),
                        receivePayload(requester.first),
                    )
                    assertEquals(
                        0,
                        lobbyManager.getLobby(lobbyCode)?.currentState()?.processedEventCount,
                    )
                    waitUntilAtLeast(routedPackets, expectedCount = 1)
                    assertEquals(1, routedPackets.get())
                    assertEquals(0, routingErrors.get())
                    assertNull(receivePayloadOrNull(requester.first))
                    assertNull(receivePayloadOrNull(otherClient.first))

                    requester.first.close()
                    otherClient.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `player count request returns deterministic error for unknown lobby`() =
        testApplication {
            val network = ServerNetwork()
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(serverScope)
            val router =
                MainServerRouter(
                    lobbyManager = lobbyManager,
                    mapper = DefaultNetworkToLobbyEventMapper(),
                )
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { null },
                    hooks = MainServerLobbyRoutingServiceHooks(),
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
                    val requester = connectSession(client, network)
                    val unknownLobbyCode = LobbyCode("PC99")

                    requester.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    LobbyPlayerCountRequest(unknownLobbyCode),
                                ),
                        ),
                    )

                    assertEquals(
                        LobbyPlayerCountErrorResponse(
                            lobbyCode = unknownLobbyCode,
                            code = LobbyPlayerCountErrorCode.LOBBY_NOT_FOUND,
                            reason = "Lobby 'PC99' wurde nicht gefunden.",
                        ),
                        receivePayload(requester.first),
                    )
                    assertNull(receivePayloadOrNull(requester.first))

                    requester.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `player count request is wired through module with lobby runtime`() =
        testApplication {
            val network = ServerNetwork()

            application {
                moduleWithLobbyRuntime(network)
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            coroutineScope {
                val hostSession = client.webSocketSession("/ws")
                receiveTestConnectionToken(hostSession)

                hostSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(CreateLobbyRequest),
                    ),
                )
                val lobbyCode =
                    (receivePayload(hostSession) as CreateLobbyResponse).lobbyCode

                hostSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Alice")),
                    ),
                )
                assertJoinLobbyResponse(
                    lobbyCode,
                    receivePayloadOfType<JoinLobbyResponse>(hostSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(1),
                        playerDisplayName = "Alice",
                        isHost = true,
                    ),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(hostSession),
                )
                assertEquals(
                    TurnStateUpdatedEvent(
                        lobbyCode = lobbyCode,
                        activePlayerId = PlayerId(1),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 1,
                        startPlayerId = PlayerId(1),
                    ),
                    receivePayloadOfType<TurnStateUpdatedEvent>(hostSession),
                )

                val joinerSession = client.webSocketSession("/ws")
                receiveTestConnectionToken(joinerSession)
                joinerSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Bob")),
                    ),
                )
                assertJoinLobbyResponse(
                    lobbyCode,
                    receivePayloadOfType<JoinLobbyResponse>(joinerSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(1),
                        playerDisplayName = "Alice",
                        isHost = true,
                    ),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(joinerSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(2),
                        playerDisplayName = "Bob",
                    ),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(joinerSession),
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(2),
                        playerDisplayName = "Bob",
                    ),
                    receivePayloadOfType<PlayerJoinedLobbyEvent>(hostSession),
                )

                hostSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(LobbyPlayerCountRequest(lobbyCode)),
                    ),
                )

                assertEquals(
                    LobbyPlayerCountResponse(
                        lobbyCode = lobbyCode,
                        playerCount = 2,
                    ),
                    receivePayload(hostSession),
                )
                assertNull(receivePayloadOrNull(hostSession))
                assertNull(receivePayloadOrNull(joinerSession))

                hostSession.close()
                joinerSession.close()
            }
        }

    private suspend fun waitUntilAtLeast(
        counter: AtomicInteger,
        expectedCount: Int,
    ) {
        withTimeout(5_000) {
            while (counter.get() < expectedCount) {
                delay(5)
            }
        }
    }

    private fun lobbyState(
        lobbyCode: LobbyCode,
        players: List<PlayerId>,
    ): GameState =
        GameState(
            lobbyCode = lobbyCode,
            lobbyOwner = players.firstOrNull(),
            players = players,
            playerDisplayNames = players.associateWith { "Player ${it.value}" },
            turnOrder = players,
        )

    private suspend fun connectSession(
        client: io.ktor.client.HttpClient,
        network: ServerNetwork,
    ) = coroutineScope {
        val session = client.webSocketSession("/ws")
        val sessionToken = receiveTestConnectionToken(session)
        session to awaitTestConnectionId(network, sessionToken)
    }

    private suspend fun receivePayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any = receiveRelevantTestPayload(session)

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any? = receiveRelevantTestPayloadOrNull(session = session, timeoutMillis = 500)

    private suspend inline fun <reified T> receivePayloadOfType(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
        maxMessages: Int = 6,
    ): T {
        repeat(maxMessages) {
            val payload = receivePayload(session)
            if (payload is T) {
                return payload
            }
        }

        throw AssertionError(
            "Expected payload of type ${T::class.java.simpleName} within $maxMessages messages.",
        )
    }

    private fun assertJoinLobbyResponse(
        lobbyCode: LobbyCode,
        payload: JoinLobbyResponse,
    ) {
        assertEquals(lobbyCode, payload.lobbyCode)
    }
}
