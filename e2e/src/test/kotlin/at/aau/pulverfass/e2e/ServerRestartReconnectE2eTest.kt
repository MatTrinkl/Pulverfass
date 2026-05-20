package at.aau.pulverfass.e2e

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.net.ServerSocket

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerRestartReconnectE2eTest {
    private val repoRoot = findRepoRoot()
    private val serverImage = "se2risiko-server:e2e"

    @BeforeAll
    fun buildServerImage() {
        runDockerCommand(
            "build",
            "--build-arg",
            "APP_VERSION=e2e",
            "--tag",
            serverImage,
            ".",
        )
    }

    @AfterAll
    fun cleanupImage() {
        runCatching {
            runDockerCommand("image", "rm", "-f", serverImage)
        }
    }

    @Test
    fun `players reconnect after full server container restart and continue in restored lobby`() =
        runBlocking {
            val projectName = "pulverfass-e2e-${System.nanoTime()}"
            val port = freePort()
            val client = HttpClient(CIO) { install(WebSockets) }

            val composeEnv =
                mapOf(
                    "SERVER_IMAGE" to serverImage,
                    "DOKKA_IMAGE" to "nginxinc/nginx-unprivileged:1.27-alpine",
                    "APP_VERSION" to "e2e",
                    "PORT" to port.toString(),
                    "DOKKA_PORT" to freePort().toString(),
                    "POSTGRES_USER" to "pulverfass",
                    "POSTGRES_PASSWORD" to "pulverfass",
                    "POSTGRES_DB" to "pulverfass",
                    "DB_HOST" to "db",
                    "DB_PORT" to "5432",
                    "DB_NAME" to "pulverfass",
                    "DB_USER" to "pulverfass",
                    "DB_PASSWORD" to "pulverfass",
                )

            try {
                runCompose(projectName, composeEnv, "up", "-d", "db", "server")
                waitForHealth(client, port)

                val aliceSession = client.webSocketSession("ws://127.0.0.1:$port/ws")
                val aliceToken = receiveConnectionToken(aliceSession)

                aliceSession.send(
                    Frame.Binary(fin = true, data = MessageCodec.encode(CreateLobbyRequest)),
                )
                val createResponse =
                    receivePayloadMatching(aliceSession) { payload ->
                        payload is CreateLobbyResponse
                    } as CreateLobbyResponse
                val lobbyCode = createResponse.lobbyCode

                aliceSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Alice")),
                    ),
                )
                assertEquals(
                    JoinLobbyResponse(lobbyCode),
                    receivePayloadMatching(aliceSession) { payload ->
                        payload == JoinLobbyResponse(lobbyCode)
                    },
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(1),
                        playerDisplayName = "Alice",
                        isHost = true,
                    ),
                    receivePayloadMatching(aliceSession) { payload ->
                        payload is PlayerJoinedLobbyEvent && payload.playerId == PlayerId(1)
                    },
                )

                val bobSession = client.webSocketSession("ws://127.0.0.1:$port/ws")
                val bobToken = receiveConnectionToken(bobSession)
                bobSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Bob")),
                    ),
                )
                assertEquals(
                    JoinLobbyResponse(lobbyCode),
                    receivePayloadMatching(bobSession) { payload ->
                        payload == JoinLobbyResponse(lobbyCode)
                    },
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(1),
                        playerDisplayName = "Alice",
                        isHost = true,
                    ),
                    receivePayloadMatching(bobSession) { payload ->
                        payload is PlayerJoinedLobbyEvent && payload.playerId == PlayerId(1)
                    },
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(2),
                        playerDisplayName = "Bob",
                        isHost = false,
                    ),
                    receivePayloadMatching(bobSession) { payload ->
                        payload is PlayerJoinedLobbyEvent && payload.playerId == PlayerId(2)
                    },
                )
                assertEquals(
                    PlayerJoinedLobbyEvent(
                        lobbyCode = lobbyCode,
                        playerId = PlayerId(2),
                        playerDisplayName = "Bob",
                        isHost = false,
                    ),
                    receivePayloadMatching(aliceSession) { payload ->
                        payload is PlayerJoinedLobbyEvent && payload.playerId == PlayerId(2)
                    },
                )

                closeQuietly(aliceSession)
                closeQuietly(bobSession)
                runCompose(projectName, composeEnv, "stop", "server")
                runCompose(projectName, composeEnv, "rm", "-f", "server")
                runCompose(projectName, composeEnv, "up", "-d", "server")
                waitForHealth(client, port)

                val aliceReconnectSession = client.webSocketSession("ws://127.0.0.1:$port/ws")
                discardHandshake(aliceReconnectSession)
                aliceReconnectSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(aliceToken)),
                    ),
                )
                val aliceReconnect =
                    receivePayloadMatching(aliceReconnectSession) { payload ->
                        payload is ReconnectResponse
                    } as ReconnectResponse
                assertEquals(
                    ReconnectResponse(
                        success = true,
                        playerId = PlayerId(1),
                        lobbyCode = lobbyCode,
                        playerDisplayName = "Alice",
                    ),
                    aliceReconnect,
                )

                val bobReconnectSession = client.webSocketSession("ws://127.0.0.1:$port/ws")
                discardHandshake(bobReconnectSession)
                bobReconnectSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(bobToken)),
                    ),
                )
                val bobReconnect =
                    receivePayloadMatching(bobReconnectSession) { payload ->
                        payload is ReconnectResponse
                    } as ReconnectResponse
                assertEquals(
                    ReconnectResponse(
                        success = true,
                        playerId = PlayerId(2),
                        lobbyCode = lobbyCode,
                        playerDisplayName = "Bob",
                    ),
                    bobReconnect,
                )

                aliceReconnectSession.send(
                    Frame.Binary(
                        fin = true,
                        data =
                            MessageCodec.encode(
                                GameStatePrivateGetRequest(lobbyCode, PlayerId(1)),
                            ),
                    ),
                )
                val alicePrivate =
                    receivePayloadMatching(aliceReconnectSession) { payload ->
                        payload is GameStatePrivateGetResponse
                    } as GameStatePrivateGetResponse
                assertEquals(lobbyCode, alicePrivate.lobbyCode)
                assertEquals(PlayerId(1), alicePrivate.recipientPlayerId)
                assertTrue(alicePrivate.stateVersion >= 2)

                bobReconnectSession.send(
                    Frame.Binary(
                        fin = true,
                        data =
                            MessageCodec.encode(
                                GameStatePrivateGetRequest(lobbyCode, PlayerId(2)),
                            ),
                    ),
                )
                val bobPrivate =
                    receivePayloadMatching(bobReconnectSession) { payload ->
                        payload is GameStatePrivateGetResponse
                    } as GameStatePrivateGetResponse
                assertEquals(lobbyCode, bobPrivate.lobbyCode)
                assertEquals(PlayerId(2), bobPrivate.recipientPlayerId)
                assertTrue(bobPrivate.stateVersion >= 2)

                closeQuietly(aliceReconnectSession)
                closeQuietly(bobReconnectSession)
            } finally {
                client.close()
                runCatching {
                    runCompose(projectName, composeEnv, "logs", "--no-color", "server", "db")
                }
                runCatching {
                    runCompose(projectName, composeEnv, "down", "-v", "--remove-orphans")
                }
            }
        }

    private suspend fun waitForHealth(
        client: HttpClient,
        port: Int,
    ) {
        withTimeout(30_000) {
            while (true) {
                val response =
                    runCatching {
                        client.get("http://127.0.0.1:$port/health")
                    }.getOrNull()
                if (response?.status == HttpStatusCode.OK && response.bodyAsText() == "OK") {
                    return@withTimeout
                }
                delay(200)
            }
        }
    }

    private suspend fun discardHandshake(session: DefaultClientWebSocketSession) {
        assertTrue(receiveRawPayload(session) is ConnectionResponse)
    }

    private suspend fun receiveConnectionToken(
        session: DefaultClientWebSocketSession,
    ): SessionToken {
        val payload = receiveRawPayload(session)
        require(payload is ConnectionResponse) {
            "Expected ConnectionResponse handshake but got ${payload::class.simpleName}."
        }
        return payload.sessionToken
    }

    private suspend fun receivePayloadMatching(
        session: DefaultClientWebSocketSession,
        maxMessages: Int = 30,
        predicate: (NetworkMessagePayload) -> Boolean,
    ): NetworkMessagePayload {
        repeat(maxMessages) {
            val payload = receiveRawPayload(session)
            if (
                payload !is ConnectionResponse &&
                payload !is GameStateDeltaEvent &&
                payload !is GameStateSnapshotBroadcast &&
                predicate(payload)
            ) {
                return payload
            }
        }
        error("Expected matching websocket payload within $maxMessages messages.")
    }

    private suspend fun receiveRawPayload(
        session: DefaultClientWebSocketSession,
    ): NetworkMessagePayload {
        val frame =
            withTimeout(5_000) {
                session.incoming.receive()
            }
        require(frame is Frame.Binary) {
            "Expected binary websocket frame."
        }
        return MessageCodec.decodePayload(frame.readBytes())
    }

    private suspend fun closeQuietly(session: DefaultClientWebSocketSession) {
        runCatching {
            session.close()
        }
    }

    private fun runCompose(
        projectName: String,
        env: Map<String, String>,
        vararg args: String,
    ): String =
        runCommand(
            listOf("docker", "compose", "-p", projectName, "-f", "compose.yaml", *args),
            env = env,
        )

    private fun runDockerCommand(vararg args: String): String {
        return runCommand(listOf("docker", *args))
    }

    private fun runCommand(
        command: List<String>,
        env: Map<String, String> = emptyMap(),
    ): String {
        val process =
            ProcessBuilder(command)
                .directory(repoRoot)
                .redirectErrorStream(true)
                .apply {
                    environment().putAll(env)
                }.start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            buildString {
                appendLine("Command failed with exit code $exitCode:")
                appendLine(command.joinToString(" "))
                if (output.isNotBlank()) {
                    appendLine(output.trim())
                }
            }
        }
        return output
    }

    private fun freePort(): Int =
        ServerSocket(0).use { socket ->
            socket.localPort
        }

    private fun findRepoRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(5) {
            if (File(current, "Dockerfile").isFile && File(current, "compose.yaml").isFile) {
                return current
            }
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate repository root containing Dockerfile and compose.yaml.")
    }
}
