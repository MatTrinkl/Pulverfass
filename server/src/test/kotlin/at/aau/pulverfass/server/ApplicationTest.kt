package at.aau.pulverfass.server

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplicationTest {
    @Test
    fun `createServerWithLobbyRuntime creates startable engine that can be stopped cleanly`() {
        val server = createServerWithLobbyRuntime(host = "127.0.0.1", port = 0)

        try {
            server.start(wait = false)
            Thread.sleep(100)
        } finally {
            server.stop(1_000, 1_000)
        }
    }

    @Test
    fun `moduleWithLobbyRuntime routes create and join requests end to end`() =
        testApplication {
            application {
                moduleWithLobbyRuntime()
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            val hostSession = client.webSocketSession("/ws")
            discardConnectionHandshake(hostSession)

            hostSession.send(
                Frame.Binary(fin = true, data = MessageCodec.encode(CreateLobbyRequest)),
            )

            val createLobbyResponse = assertIs<CreateLobbyResponse>(receivePayload(hostSession))
            val lobbyCode = createLobbyResponse.lobbyCode

            hostSession.send(
                Frame.Binary(
                    fin = true,
                    data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Alice")),
                ),
            )

            assertEquals(JoinLobbyResponse(lobbyCode), receivePayload(hostSession))
            assertEquals(
                PlayerJoinedLobbyEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(1),
                    playerDisplayName = "Alice",
                    isHost = true,
                ),
                receivePayload(hostSession),
            )

            val guestSession = client.webSocketSession("/ws")
            discardConnectionHandshake(guestSession)

            guestSession.send(
                Frame.Binary(
                    fin = true,
                    data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Bob")),
                ),
            )

            assertEquals(JoinLobbyResponse(lobbyCode), receivePayload(guestSession))
            assertEquals(
                PlayerJoinedLobbyEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(1),
                    playerDisplayName = "Alice",
                    isHost = true,
                ),
                receivePayload(guestSession),
            )
            assertEquals(
                PlayerJoinedLobbyEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(2),
                    playerDisplayName = "Bob",
                    isHost = false,
                ),
                receivePayload(guestSession),
            )
            assertEquals(
                PlayerJoinedLobbyEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(2),
                    playerDisplayName = "Bob",
                    isHost = false,
                ),
                receivePayload(hostSession),
            )

            hostSession.close()
            guestSession.close()
        }

    private suspend fun discardConnectionHandshake(session: DefaultClientWebSocketSession) {
        val payload = receivePayload(session)
        assertTrue(payload is ConnectionResponse)
    }

    private suspend fun receivePayload(
        session: DefaultClientWebSocketSession,
    ): NetworkMessagePayload {
        val frame =
            withTimeout(5_000) {
                session.incoming.receive()
            }

        assertTrue(frame is Frame.Binary)
        return MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T)
        return value as T
    }
}
