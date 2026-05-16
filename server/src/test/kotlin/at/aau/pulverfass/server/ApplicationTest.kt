package at.aau.pulverfass.server

import at.aau.pulverfass.server.persistence.LobbyPersistenceCallbacks
import at.aau.pulverfass.server.session.SessionContextRegistry
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class ApplicationTest {
    private object ReadyDatabaseProbe : DatabaseReadinessProbe {
        override fun readiness(): DatabaseReadiness = DatabaseReadiness(DatabaseReadinessState.UP)
    }

    private object DownDatabaseProbe : DatabaseReadinessProbe {
        override fun readiness(): DatabaseReadiness =
            DatabaseReadiness(
                state = DatabaseReadinessState.DOWN,
                detail = "Connection refused",
            )
    }

    @Test
    fun `module exposes disabled ready endpoint when database is not configured`() =
        testApplication {
            application {
                module(
                    runtimeConfig = ServerRuntimeConfig(appVersion = "v1.2.3"),
                )
            }

            val response = client.get("/ready")

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals(
                "NOT_READY version=v1.2.3 database=disabled detail=Database is not configured.",
                response.bodyAsText(),
            )
        }

    @Test
    fun `module closes readiness probe when application stops`() {
        var closed = false
        val probe =
            object : DatabaseReadinessProbe {
                override fun readiness(): DatabaseReadiness =
                    DatabaseReadiness(DatabaseReadinessState.UP)

                override fun close() {
                    closed = true
                }
            }

        testApplication {
            application {
                module(
                    databaseReadinessProbe = probe,
                )
            }
        }

        assertTrue(closed)
    }

    @Test
    fun `prepareServerEngine returns null for migration only mode after running migrations`() {
        var migrationConfig: DatabaseRuntimeConfig? = null
        var serverFactoryCalled = false

        val engine =
            prepareServerEngine(
                runtimeConfig = ServerRuntimeConfig(appVersion = "v1.2.3"),
                serverMode = "migrate",
                migrationRunner = { config -> migrationConfig = config },
                serverFactory = { _, _, _, _, _, _ ->
                    serverFactoryCalled = true
                    createServer(host = "127.0.0.1", port = 0)
                },
            )

        assertNull(engine)
        assertNotNull(migrationConfig)
        assertTrue(!serverFactoryCalled)
    }

    @Test
    fun `prepareServerEngine wires probe and callbacks into runtime server creation`() {
        val callbacks = LobbyPersistenceCallbacks.disabled()
        val probe = ReadyDatabaseProbe
        var migrated = false
        var capturedHost: String? = null
        var capturedPort: Int? = null
        var capturedRuntimeConfig: ServerRuntimeConfig? = null
        var capturedProbe: DatabaseReadinessProbe? = null
        var capturedCallbacks: LobbyPersistenceCallbacks? = null

        val engine =
            prepareServerEngine(
                runtimeConfig =
                    ServerRuntimeConfig(
                        host = "127.0.0.1",
                        port = 9090,
                        appVersion = "v1.2.3",
                    ),
                migrationRunner = { migrated = true },
                persistenceCallbacksFactory = { callbacks },
                databaseReadinessProbeFactory =
                    { _, persistenceCallbacks ->
                        assertEquals(callbacks, persistenceCallbacks)
                        probe
                    },
                serverFactory =
                    { host, port, _, runtimeConfig, databaseReadinessProbe, persistenceCallbacks ->
                        capturedHost = host
                        capturedPort = port
                        capturedRuntimeConfig = runtimeConfig
                        capturedProbe = databaseReadinessProbe
                        capturedCallbacks = persistenceCallbacks
                        createServer(host = "127.0.0.1", port = 0)
                    },
            )

        assertTrue(migrated)
        assertNotNull(engine)
        assertEquals("127.0.0.1", capturedHost)
        assertEquals(9090, capturedPort)
        assertEquals("v1.2.3", capturedRuntimeConfig?.appVersion)
        assertEquals(probe, capturedProbe)
        assertEquals(callbacks, capturedCallbacks)
    }

    @Test
    fun `prepareServerEngine uses default composite readiness probe wiring`() {
        var capturedProbe: DatabaseReadinessProbe? = null

        val engine =
            prepareServerEngine(
                runtimeConfig = ServerRuntimeConfig(host = "127.0.0.1", port = 0),
                migrationRunner = {},
                persistenceCallbacksFactory = { LobbyPersistenceCallbacks.disabled() },
                serverFactory = { _, _, _, _, databaseReadinessProbe, _ ->
                    capturedProbe = databaseReadinessProbe
                    createServer(host = "127.0.0.1", port = 0)
                },
            )

        assertNotNull(engine)
        assertEquals(
            DatabaseReadiness(
                state = DatabaseReadinessState.DISABLED,
                detail = "Database is not configured.",
            ),
            capturedProbe?.readiness(),
        )
    }

    @Test
    fun `createDatabaseReadinessProbe returns disabled probe when database is not configured`() {
        val probe = createDatabaseReadinessProbe(DatabaseRuntimeConfig())

        assertEquals(
            DatabaseReadiness(
                state = DatabaseReadinessState.DISABLED,
                detail = "Database is not configured.",
            ),
            probe.readiness(),
        )
    }

    @Test
    fun `createDatabaseReadinessProbe returns postgres probe when database is configured`() {
        val config = requireExternalTestDatabaseConfig()
        val probe = createDatabaseReadinessProbe(config)

        try {
            assertEquals(DatabaseReadinessState.UP, probe.readiness().state)
        } finally {
            probe.close()
        }
    }

    @Test
    fun `createLobbyPersistenceCallbacks returns disabled callbacks without db config`() =
        run {
            val callbacks = createLobbyPersistenceCallbacks(DatabaseRuntimeConfig())

            assertEquals(
                DatabaseReadiness(
                    state = DatabaseReadinessState.DISABLED,
                    detail = "Lobby persistence is disabled.",
                ),
                callbacks.readiness(),
            )
        }

    @Test
    fun `createLobbyPersistenceCallbacks returns active callbacks when database is configured`() {
        val config = requireExternalTestDatabaseConfig()
        val callbacks = createLobbyPersistenceCallbacks(config)

        try {
            assertEquals(DatabaseReadinessState.UP, callbacks.readiness().state)
        } finally {
            callbacks.close()
        }
    }

    @Test
    fun `formatReadinessResponse condenses whitespace in details`() {
        val response =
            formatReadinessResponse(
                appVersion = "v1.2.3",
                readiness =
                    DatabaseReadiness(
                        state = DatabaseReadinessState.DOWN,
                        detail = "Connection\n refused\t by  peer",
                    ),
            )

        assertEquals(
            "NOT_READY version=v1.2.3 database=down detail=Connection refused by peer",
            response,
        )
    }

    @Test
    fun `persistReconnectSessionIfPossible returns when session is missing`() {
        persistReconnectSessionIfPossible(
            network = ServerNetwork(),
            sessionStore = null,
            sessionContextRegistry = SessionContextRegistry(),
            sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174600"),
        )
    }

    @Test
    fun `persistReconnectSessionIfPossible returns when session context is missing`() {
        val network = ServerNetwork()
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174601")
        network.sessionManager.restoreDetachedSession(
            sessionToken = sessionToken,
            expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
        )

        persistReconnectSessionIfPossible(
            network = network,
            sessionStore = null,
            sessionContextRegistry = SessionContextRegistry(),
            sessionToken = sessionToken,
        )
    }

    @Test
    fun `persistReconnectSessionIfPossible returns when session context has no player`() {
        val network = ServerNetwork()
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174602")
        val sessionContextRegistry = SessionContextRegistry()
        network.sessionManager.restoreDetachedSession(
            sessionToken = sessionToken,
            expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
        )
        sessionContextRegistry.updateLobbyContext(
            sessionToken = sessionToken,
            lobbyCode = LobbyCode("AB12"),
            playerDisplayName = "Alice",
        )

        persistReconnectSessionIfPossible(
            network = network,
            sessionStore = null,
            sessionContextRegistry = sessionContextRegistry,
            sessionToken = sessionToken,
        )
    }

    @Test
    fun `module exposes health endpoint`() =
        testApplication {
            application {
                module()
            }

            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("OK", response.bodyAsText())
        }

    @Test
    fun `module exposes version endpoint`() =
        testApplication {
            application {
                module(
                    runtimeConfig = ServerRuntimeConfig(appVersion = "v1.2.3"),
                )
            }

            val response = client.get("/version")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("v1.2.3", response.bodyAsText())
        }

    @Test
    fun `module exposes ready endpoint when database is ready`() =
        testApplication {
            application {
                module(
                    runtimeConfig = ServerRuntimeConfig(appVersion = "v1.2.3"),
                    databaseReadinessProbe = ReadyDatabaseProbe,
                )
            }

            val response = client.get("/ready")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("READY version=v1.2.3 database=up", response.bodyAsText())
        }

    @Test
    fun `module exposes not ready endpoint when database is down`() =
        testApplication {
            application {
                module(
                    runtimeConfig = ServerRuntimeConfig(appVersion = "v1.2.3"),
                    databaseReadinessProbe = DownDatabaseProbe,
                )
            }

            val response = client.get("/ready")

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals(
                "NOT_READY version=v1.2.3 database=down detail=Connection refused",
                response.bodyAsText(),
            )
        }

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
        val payload = receiveRawTestPayload(session)
        assertTrue(payload is ConnectionResponse)
    }

    private suspend fun receivePayload(
        session: DefaultClientWebSocketSession,
    ): NetworkMessagePayload {
        repeat(20) {
            val payload = receiveRawTestPayload(session)
            if (
                payload !is ConnectionResponse &&
                payload !is GameStateDeltaEvent &&
                payload !is GameStateSnapshotBroadcast &&
                payload !is TurnStateUpdatedEvent
            ) {
                return payload
            }
        }
        throw AssertionError("Expected lobby payload within 20 messages.")
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T)
        return value as T
    }

    private fun requireExternalTestDatabaseConfig(): DatabaseRuntimeConfig {
        val jdbcUrl = System.getenv("PULVERFASS_TEST_DB_URL")?.trim().orEmpty()
        val user = System.getenv("PULVERFASS_TEST_DB_USER")?.trim().orEmpty()
        val password = System.getenv("PULVERFASS_TEST_DB_PASSWORD")?.trim().orEmpty()
        assumeTrue(jdbcUrl.isNotEmpty() && user.isNotEmpty()) {
            "External PostgreSQL test database configuration is required for this test."
        }
        return DatabaseRuntimeConfig(
            url = jdbcUrl,
            user = user,
            password = password,
        )
    }
}
