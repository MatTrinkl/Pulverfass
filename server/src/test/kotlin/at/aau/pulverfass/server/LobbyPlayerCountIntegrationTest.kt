package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingServiceHooks
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.request.LobbyPlayerCountRequest
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LobbyPlayerCountIntegrationTest {
    @Test
    fun `player count request replies only to requesting client`() =
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
}
