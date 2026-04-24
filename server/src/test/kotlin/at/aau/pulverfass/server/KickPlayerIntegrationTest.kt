package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequest
import at.aau.pulverfass.shared.message.lobby.response.KickPlayerResponse
import at.aau.pulverfass.shared.message.lobby.response.error.KickPlayerErrorResponse
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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

                    val expectedTurnStateEvent =
                        TurnStateUpdatedEvent(
                            lobbyCode = lobbyCode,
                            activePlayerId = ownerId,
                            turnPhase = TurnPhase.REINFORCEMENTS,
                            turnCount = 1,
                            startPlayerId = ownerId,
                        )
                    val expectedDelta =
                        GameStateDeltaEvent(
                            lobbyCode = lobbyCode,
                            fromVersion = 1,
                            toVersion = 1,
                            events = listOf(expectedTurnStateEvent),
                        )

                    assertEquals(expectedDelta, receivePayload(ownerSession.first))
                    assertEquals(KickPlayerResponse(), receivePayload(ownerSession.first))
                    assertEquals(
                        PlayerKickedLobbyEvent(
                            lobbyCode = lobbyCode,
                            targetPlayerId = targetId,
                            requesterPlayerId = ownerId,
                        ),
                        receivePayload(ownerSession.first),
                    )
                    assertEquals(expectedTurnStateEvent, receivePayload(ownerSession.first))
                    assertEquals(expectedDelta, receivePayload(otherSession.first))
                    assertEquals(
                        PlayerKickedLobbyEvent(
                            lobbyCode = lobbyCode,
                            targetPlayerId = targetId,
                            requesterPlayerId = ownerId,
                        ),
                        receivePayload(otherSession.first),
                    )
                    assertEquals(expectedTurnStateEvent, receivePayload(otherSession.first))
                    assertNull(receivePayloadOrNull(targetSession.first))
                    assertNull(receivePayloadOrNull(ownerSession.first))
                    assertNull(receivePayloadOrNull(otherSession.first))
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
        val frame = assertIs<Frame.Binary>(withTimeout(5_000) { session.incoming.receive() })
        return MessageCodec.decodePayload(frame.readBytes())
    }

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): NetworkMessagePayload? {
        val frame =
            withTimeoutOrNull(200) {
                session.incoming.receive()
            } ?: return null
        val binary = assertIs<Frame.Binary>(frame)
        return MessageCodec.decodePayload(binary.readBytes())
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T)
        return value as T
    }
}
