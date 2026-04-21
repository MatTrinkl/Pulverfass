package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpReason
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
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

class GameStateCatchUpIntegrationTest {
    @Test
    fun `client can recover via full catch up snapshot after missing delta`() =
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
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("CU01")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val definition = defaultMapDefinition()
            val state =
                GameState
                    .initial(
                        lobbyCode = lobbyCode,
                        mapDefinition = definition,
                        players = listOf(playerOne, playerTwo),
                        playerDisplayNames = mapOf(playerOne to "One", playerTwo to "Two"),
                    ).copy(stateVersion = 7)
            lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = state)
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val playerOneSession =
                        connectSessionWithPlayer(
                            client = client,
                            network = network,
                            playerId = playerOne,
                            playersByConnection = playersByConnection,
                        )

                    playerOneSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    GameStateCatchUpRequest(
                                        lobbyCode = lobbyCode,
                                        clientStateVersion = 4,
                                        reason = GameStateCatchUpReason.MISSING_DELTA,
                                    ),
                                ),
                        ),
                    )

                    val response = receivePayload(playerOneSession.first) as GameStateCatchUpResponse
                    assertEquals(lobbyCode, response.lobbyCode)
                    assertEquals(7, response.stateVersion)
                    assertEquals(definition.mapHash, response.determinism.mapHash)
                    assertEquals(definition.schemaVersion, response.determinism.schemaVersion)
                    assertEquals(state.turnState?.activePlayerId, response.turnState.activePlayerId)
                    assertEquals(state.turnState?.turnPhase, response.turnState.turnPhase)
                    assertEquals(definition.territories.size, response.definition.territories.size)
                    assertEquals(definition.territories.size, response.territoryStates.size)
                    assertNull(receivePayloadOrNull(playerOneSession.first))

                    playerOneSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    @Test
    fun `only lobby member can request catch up snapshot`() =
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
            val routingService =
                MainServerLobbyRoutingService(
                    network = network,
                    router = router,
                    lobbyManager = lobbyManager,
                    playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                    hooks = MainServerLobbyRoutingServiceHooks(),
                )

            application {
                module(network)
            }

            val lobbyCode = LobbyCode("CU02")
            val playerOne = PlayerId(1)
            val outsider = PlayerId(99)
            lobbyManager.createLobby(
                lobbyCode = lobbyCode,
                initialState =
                    GameState.initial(
                        lobbyCode = lobbyCode,
                        mapDefinition = defaultMapDefinition(),
                        players = listOf(playerOne),
                        playerDisplayNames = mapOf(playerOne to "One"),
                    ),
            )
            routingService.start(serverScope)

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val outsiderSession =
                        connectSessionWithPlayer(
                            client = client,
                            network = network,
                            playerId = outsider,
                            playersByConnection = playersByConnection,
                        )

                    outsiderSession.first.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    GameStateCatchUpRequest(
                                        lobbyCode = lobbyCode,
                                        clientStateVersion = 0,
                                        reason = GameStateCatchUpReason.AFTER_RECONNECT,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        GameStateCatchUpErrorResponse(
                            code = GameStateCatchUpErrorCode.NOT_IN_GAME,
                            reason = "Spieler '99' ist nicht Teil von Lobby 'CU02'.",
                        ),
                        receivePayload(outsiderSession.first),
                    )
                    assertNull(receivePayloadOrNull(outsiderSession.first))

                    outsiderSession.first.close()
                }
            } finally {
                routingService.stop()
                lobbyManager.shutdownAll()
                serverScope.cancel()
            }
        }

    private suspend fun connectSessionWithPlayer(
        client: io.ktor.client.HttpClient,
        network: ServerNetwork,
        playerId: PlayerId,
        playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
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
        val connectionId = connectedDeferred.await().connectionId
        playersByConnection[connectionId] = playerId
        session to connectionId
    }

    private suspend fun receivePayload(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any {
        val frame = withTimeout(5_000) { session.incoming.receive() }
        assertTrue(frame is Frame.Binary)
        return MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
    }

    private suspend fun receivePayloadOrNull(
        session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession,
    ): Any? =
        withTimeoutOrNull(500) {
            val frame = session.incoming.receive()
            assertTrue(frame is Frame.Binary)
            MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
        }

    private fun defaultMapDefinition() = at.aau.pulverfass.shared.map.config.MapConfigLoader.loadDefault()
}
