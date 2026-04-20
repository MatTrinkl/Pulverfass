package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequest
import at.aau.pulverfass.shared.message.lobby.response.KickPlayerResponse
import at.aau.pulverfass.shared.message.lobby.response.error.KickPlayerErrorResponse
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class KickPlayerIntegrationTest {
    @Test
    fun `kick request by owner removes player and broadcasts event`() =
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

            val lobbyCode = LobbyCode("KICK")
            val ownerId = PlayerId(1)
            val targetId = PlayerId(2)
            val otherPlayerId = PlayerId(3)
            lobbyManager.createLobby(
                lobbyCode,
                GameState(
                    lobbyCode = lobbyCode,
                    lobbyOwner = ownerId,
                    players = listOf(ownerId, targetId, otherPlayerId),
                    playerDisplayNames =
                        mapOf(
                            ownerId to "Owner",
                            targetId to "Target",
                            otherPlayerId to "Other",
                        ),
                    activePlayer = ownerId,
                    turnOrder = listOf(ownerId, targetId, otherPlayerId),
                    status = GameStatus.RUNNING,
                ),
            )

            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
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
                    val ownerSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = ownerId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val targetSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = targetId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val otherSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = otherPlayerId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    val kickRequest =
                        KickPlayerRequest(
                            lobbyCode = lobbyCode,
                            targetPlayerId = targetId,
                            requesterPlayerId = ownerId,
                        )

                    ownerSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(kickRequest),
                        ),
                    )

                    assertEquals(KickPlayerResponse(), receivePayload(ownerSession.first))
                    assertEquals(
                        PlayerKickedLobbyEvent(
                            lobbyCode = lobbyCode,
                            targetPlayerId = targetId,
                            requesterPlayerId = ownerId,
                        ),
                        receivePayload(ownerSession.first),
                    )
                    assertEquals(
                        PlayerKickedLobbyEvent(
                            lobbyCode = lobbyCode,
                            targetPlayerId = targetId,
                            requesterPlayerId = ownerId,
                        ),
                        receivePayload(otherSession.first),
                    )
                    assertNull(receivePayloadOrNull(targetSession.first))
                    assertEquals(
                        listOf(ownerId, otherPlayerId),
                        lobbyManager.getLobby(lobbyCode)?.currentState()?.players,
                    )

                    ownerSession.first.close()
                    targetSession.first.close()
                    otherSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `kick request by non-owner returns error`() =
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

            val lobbyCode = LobbyCode("KIC2")
            val ownerId = PlayerId(1)
            val requesterId = PlayerId(2)
            val targetId = PlayerId(3)
            lobbyManager.createLobby(
                lobbyCode,
                GameState(
                    lobbyCode = lobbyCode,
                    lobbyOwner = ownerId,
                    players = listOf(ownerId, requesterId, targetId),
                    playerDisplayNames =
                        mapOf(
                            ownerId to "Owner",
                            requesterId to "Requester",
                            targetId to "Target",
                        ),
                    activePlayer = ownerId,
                    turnOrder = listOf(ownerId, requesterId, targetId),
                    status = GameStatus.RUNNING,
                ),
            )

            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
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
                    val ownerSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = ownerId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val requesterSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = requesterId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )
                    val targetSession =
                        connectSessionWithConnection(
                            client = client,
                            network = network,
                            playerId = targetId,
                            playersByConnection = playersByConnection,
                            connectionsByPlayer = connectionsByPlayer,
                        )

                    val kickRequest =
                        KickPlayerRequest(
                            lobbyCode = lobbyCode,
                            targetPlayerId = targetId,
                            requesterPlayerId = requesterId,
                        )

                    requesterSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(kickRequest),
                        ),
                    )

                    val decoded = receivePayload(requesterSession.first)
                    val errorResponse = assertIs<KickPlayerErrorResponse>(decoded)
                    assertTrue(
                        errorResponse.reason.startsWith("Nur der Lobby Owner kann Spieler kicken"),
                    )
                    assertNull(receivePayloadOrNull(ownerSession.first))
                    assertNull(receivePayloadOrNull(targetSession.first))
                    assertEquals(
                        listOf(ownerId, requesterId, targetId),
                        lobbyManager.getLobby(lobbyCode)?.currentState()?.players,
                    )

                    ownerSession.first.close()
                    requesterSession.first.close()
                    targetSession.first.close()
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
        val session = client.webSocketSession("/ws")
        val sessionToken = discardConnectionHandshake(session)
        val connectionId = awaitConnectionId(network, sessionToken)
        playersByConnection[connectionId] = playerId
        connectionsByPlayer[playerId] = connectionId
        session to connectionId
    }

    private suspend fun receivePayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ) = run {
        val frame = assertIs<Frame.Binary>(withTimeout(5_000) { session.incoming.receive() })
        MessageCodec.decodePayload(frame.readBytes())
    }

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ) = withTimeoutOrNull(500) {
        val frame = assertIs<Frame.Binary>(session.incoming.receive())
        MessageCodec.decodePayload(frame.readBytes())
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T)
        return value as T
    }

    private suspend fun discardConnectionHandshake(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): SessionToken {
        val payload = receivePayload(session)
        val response = assertIs<ConnectionResponse>(payload)
        return response.sessionToken
    }

    private suspend fun awaitConnectionId(
        network: ServerNetwork,
        sessionToken: SessionToken,
    ): ConnectionId {
        return withTimeout(5_000) {
            var connectionId: ConnectionId? = null
            while (connectionId == null) {
                connectionId = network.sessionManager.getByToken(sessionToken)?.connectionId
                if (connectionId == null) {
                    delay(5)
                }
            }
            connectionId
        }
    }
}
