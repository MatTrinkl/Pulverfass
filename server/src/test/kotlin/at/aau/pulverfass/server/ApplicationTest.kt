package at.aau.pulverfass.server

import at.aau.pulverfass.server.logging.ServerLoggerNames
import at.aau.pulverfass.server.persistence.DatabaseBackedLobbyPersistenceGateway
import at.aau.pulverfass.server.persistence.FakeConnectionScript
import at.aau.pulverfass.server.persistence.FakeJdbcDataSource
import at.aau.pulverfass.server.persistence.FakePreparedStatementScript
import at.aau.pulverfass.server.persistence.JdbcLobbyReconnectSessionStore
import at.aau.pulverfass.server.persistence.LobbyPersistenceCallbacks
import at.aau.pulverfass.server.session.SessionContextRegistry
import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.message.connection.event.GlobalPlayerCountEvent
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PlayerCountUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ApplicationTest {
    private val mapDefinition =
        at.aau.pulverfass.server.map.ClasspathMapDefinitionRepository
            .loadDefault()
            .defaultMapDefinition()

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
    fun `configured readiness probe is postgres typed without connecting`() {
        val probe =
            createDatabaseReadinessProbe(
                DatabaseRuntimeConfig(
                    host = "127.0.0.1",
                    port = 5432,
                    name = "pulverfass",
                    user = "postgres",
                    password = "postgres",
                ),
            )

        try {
            assertInstanceOf(PostgresDatabaseReadinessProbe::class.java, probe)
        } finally {
            probe.close()
        }
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
    fun `configured persistence callbacks are database backed without connecting`() {
        val callbacks =
            createLobbyPersistenceCallbacks(
                DatabaseRuntimeConfig(
                    host = "127.0.0.1",
                    port = 5432,
                    name = "pulverfass",
                    user = "postgres",
                    password = "postgres",
                ),
            )

        try {
            assertInstanceOf(
                DatabaseBackedLobbyPersistenceGateway::class.java,
                callbacks,
            )
        } finally {
            callbacks.close()
        }
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
    fun `persistReconnectSessionIfPossible upserts session for player context`() {
        val delete =
            FakePreparedStatementScript(
                expectedSqlFragment = "DELETE FROM lobby_reconnect_sessions",
                updateCount = 1,
            )
        val insert =
            FakePreparedStatementScript(
                expectedSqlFragment = "INSERT INTO lobby_reconnect_sessions",
                updateCount = 1,
            )
        val connection = FakeConnectionScript(listOf(delete, insert))
        val store =
            JdbcLobbyReconnectSessionStore(
                FakeJdbcDataSource(listOf(connection)),
            )
        val network = ServerNetwork()
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174603")
        val sessionContextRegistry = SessionContextRegistry()

        network.sessionManager.restoreDetachedSession(
            sessionToken = sessionToken,
            expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
        )
        sessionContextRegistry.assignPlayer(sessionToken, PlayerId(21))
        sessionContextRegistry.updateLobbyContext(sessionToken, LobbyCode("AB12"), "Alice")

        persistReconnectSessionIfPossible(
            network = network,
            sessionStore = store,
            sessionContextRegistry = sessionContextRegistry,
            sessionToken = sessionToken,
        )

        assertTrue(connection.committed)
        assertEquals(21L, delete.lastParameters[1])
        assertEquals("AB12", insert.lastParameters[3])
        assertEquals("ALICE", insert.lastParameters[4])
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
    fun `module exposes internal metrics endpoint`() =
        testApplication {
            application {
                module(
                    runtimeConfig =
                        ServerRuntimeConfig(
                            appVersion = "v1.2.3",
                            commitSha = "abcdef1234567890",
                        ),
                    databaseReadinessProbe = ReadyDatabaseProbe,
                )
            }

            val response = client.get("/metrics")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("pulverfass_server_info"))
            assertTrue(body.contains("version=\"v1.2.3\""))
            assertTrue(body.contains("commit=\"abcdef1234567890\""))
            assertTrue(body.contains("database_configured=\"false\""))
            assertTrue(body.contains("pulverfass_database_readiness{state=\"up\"} 1"))
            assertTrue(body.contains("pulverfass_server_uptime_seconds"))
        }

    @Test
    fun `formatMetricsResponse escapes labels and clamps uptime`() {
        val response =
            formatMetricsResponse(
                runtimeConfig =
                    ServerRuntimeConfig(
                        appVersion = "v1.2.3 local",
                        commitSha = "abc\"def",
                    ),
                readiness = DatabaseReadiness(DatabaseReadinessState.DOWN),
                startedAtEpochMillis = 5_000,
                nowEpochMillis = 3_000,
            )

        assertTrue(response.contains("version=\"v1.2.3 local\""))
        assertTrue(response.contains("commit=\"abc\\\"def\""))
        assertTrue(response.contains("pulverfass_database_readiness{state=\"down\"} 0"))
        assertTrue(response.contains("pulverfass_server_uptime_seconds 0"))
    }

    @Test
    fun `metrics endpoint internal host filter allows local and private addresses`() {
        assertTrue(isInternalMetricsRequest("localhost"))
        assertTrue(isInternalMetricsRequest("127.0.0.1"))
        assertTrue(isInternalMetricsRequest("[::1]"))
        assertTrue(isInternalMetricsRequest("10.1.2.3"))
        assertTrue(isInternalMetricsRequest("172.16.0.1"))
        assertTrue(isInternalMetricsRequest("172.31.255.255"))
        assertTrue(isInternalMetricsRequest("192.168.1.10"))

        assertFalse(isInternalMetricsRequest("8.8.8.8"))
        assertFalse(isInternalMetricsRequest("172.15.0.1"))
        assertFalse(isInternalMetricsRequest("172.32.0.1"))
    }

    @Test
    fun `transport only module still exposes standard http endpoints`() =
        testApplication {
            application {
                module(ServerWebSocketTransport())
            }

            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("OK", response.bodyAsText())
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
            runBlocking {
                withTimeout(5_000) {
                    server.resolvedConnectors()
                }
            }
        } finally {
            server.stop(1_000, 1_000)
        }
    }

    @Test
    fun `createServer transport overload creates startable engine that can be stopped cleanly`() {
        val server =
            createServer(
                host = "127.0.0.1",
                port = 0,
                transport = ServerWebSocketTransport(),
            )

        try {
            server.start(wait = false)
            runBlocking {
                withTimeout(5_000) {
                    server.resolvedConnectors()
                }
            }
        } finally {
            server.stop(1_000, 1_000)
        }
    }

    @Test
    fun `cleanupTerminalLobbyState removes lobby without persistence store`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lobbyManager = at.aau.pulverfass.server.lobby.runtime.LobbyManager(scope = scope)
        val lobbyCode = LobbyCode("CL12")

        try {
            lobbyManager.createLobby(lobbyCode)

            runBlocking {
                cleanupTerminalLobbyState(
                    lobbyCode = lobbyCode,
                    lobbyManager = lobbyManager,
                    sessionContextRegistry = SessionContextRegistry(),
                    recoveryStore = null,
                )
            }

            assertNull(lobbyManager.getLobby(lobbyCode))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `startup helper functions classify recoverable states and cleanup candidates`() {
        val waiting =
            GameState
                .initial(
                    lobbyCode = LobbyCode("ST11"),
                    mapDefinition = mapDefinition,
                    players = listOf(PlayerId(3), PlayerId(7)),
                )
                .copy(status = GameStatus.WAITING_FOR_PLAYERS)
        val running =
            GameState
                .initial(
                    lobbyCode = LobbyCode("ST12"),
                    mapDefinition = mapDefinition,
                    players = listOf(PlayerId(2)),
                )
                .copy(status = GameStatus.RUNNING)
        val finished =
            GameState
                .initial(
                    lobbyCode = LobbyCode("ST13"),
                    mapDefinition = mapDefinition,
                )
                .copy(status = GameStatus.FINISHED)
        val closed =
            GameState
                .initial(
                    lobbyCode = LobbyCode("ST14"),
                    mapDefinition = mapDefinition,
                )
                .copy(status = GameStatus.CLOSED)

        assertTrue(invokeBooleanHelper("isRecoverableOnStartup", waiting))
        assertTrue(invokeBooleanHelper("isRecoverableOnStartup", running))
        assertTrue(invokeBooleanHelper("isRecoverableOnStartup", finished))
        assertFalse(invokeBooleanHelper("isRecoverableOnStartup", closed))
        assertFalse(invokeBooleanHelper("requiresTerminalCleanup", finished))
        assertTrue(invokeBooleanHelper("requiresTerminalCleanup", closed))
        assertFalse(invokeBooleanHelper("requiresTerminalCleanup", waiting))
        assertFalse(invokeBooleanHelper("requiresTerminalCleanup", running))
        assertEquals(
            7L,
            invokeMaxPlayerId(listOf(waiting, running)),
        )
        assertEquals(0L, invokeMaxPlayerId(emptyList()))
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

            assertJoinLobbyResponse(lobbyCode, receivePayload(hostSession))

            assertEquals(
                PlayerJoinedLobbyEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(1),
                    playerDisplayName = "ALICE",
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

            assertJoinLobbyResponse(lobbyCode, receivePayload(guestSession))

            assertEquals(
                PlayerJoinedLobbyEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(1),
                    playerDisplayName = "ALICE",
                    isHost = true,
                ),
                receivePayload(guestSession),
            )
            assertEquals(
                PlayerJoinedLobbyEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(2),
                    playerDisplayName = "BOB",
                    isHost = false,
                ),
                receivePayload(guestSession),
            )
            assertEquals(
                PlayerJoinedLobbyEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(2),
                    playerDisplayName = "BOB",
                    isHost = false,
                ),
                receivePayload(hostSession),
            )

            hostSession.close()
            guestSession.close()
        }

    @Test
    fun `moduleWithLobbyRuntime logs correlated create join snapshot and leave flows`() =
        testApplication {
            val context = LoggerFactory.getILoggerFactory() as LoggerContext
            val routingLogger =
                context.getLogger("${ServerLoggerNames.TECHNICAL}.MainServerLobbyRoutingService")
            val appender =
                ListAppender<ILoggingEvent>().apply {
                    this.context = context
                    start()
                }
            routingLogger.addAppender(appender)

            try {
                application {
                    moduleWithLobbyRuntime()
                }

                val client =
                    createClient {
                        install(WebSockets)
                    }
                val session = client.webSocketSession("/ws")
                discardConnectionHandshake(session)

                session.send(
                    Frame.Binary(fin = true, data = MessageCodec.encode(CreateLobbyRequest)),
                )
                val createLobbyResponse = assertIs<CreateLobbyResponse>(receivePayload(session))
                val lobbyCode = createLobbyResponse.lobbyCode

                session.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(lobbyCode, "Alice")),
                    ),
                )
                assertEquals(JoinLobbyResponse(lobbyCode, PlayerId(1)), receivePayload(session))
                assertIs<PlayerJoinedLobbyEvent>(receivePayload(session))

                session.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(MapGetRequest(lobbyCode)),
                    ),
                )
                assertIs<MapGetResponse>(receivePayload(session))

                session.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(LeaveLobbyRequest(lobbyCode)),
                    ),
                )
                assertEquals(LeaveLobbyResponse(lobbyCode), receivePayload(session))
                session.close()

                /**
                 * Auf den letzten (Leave-)Completion-Log warten; die früheren Logs
                 * entstehen davor auf derselben Verbindungs-Coroutine und sind dann
                 * ebenfalls vorhanden.
                 */
                val messages =
                    awaitLoggedMessages(appender) { logged ->
                        logged.any {
                            it.contains("Request completed") &&
                                it.contains("messageType=LOBBY_LEAVE_REQUEST") &&
                                it.contains("responseType=LeaveLobbyResponse")
                        }
                    }
                assertTrue(
                    messages.any {
                        it.contains("Request received") &&
                            it.contains("messageType=LOBBY_CREATE_REQUEST") &&
                            it.contains("requestId=srv-")
                    },
                )
                assertTrue(
                    messages.any {
                        it.contains("Request completed") &&
                            it.contains("responseType=CreateLobbyResponse") &&
                            it.contains("lobbyId=${lobbyCode.value}")
                    },
                )
                assertTrue(
                    messages.any {
                        it.contains("Request completed") &&
                            it.contains("messageType=LOBBY_JOIN_REQUEST") &&
                            it.contains("responseType=JoinLobbyResponse") &&
                            it.contains("playerId=1")
                    },
                )
                assertTrue(
                    messages.any {
                        it.contains("Request completed") &&
                            it.contains("messageType=LOBBY_MAP_GET_REQUEST") &&
                            it.contains("responseType=MapGetResponse") &&
                            it.contains("mapHash=")
                    },
                )
                assertTrue(
                    messages.any {
                        it.contains("Request completed") &&
                            it.contains("messageType=LOBBY_LEAVE_REQUEST") &&
                            it.contains("responseType=LeaveLobbyResponse")
                    },
                )
            } finally {
                routingLogger.detachAppender(appender)
            }
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
                payload !is TurnStateUpdatedEvent &&
                payload !is PlayerCountUpdateEvent &&
                payload !is GlobalPlayerCountEvent
            ) {
                return payload
            }
        }
        throw AssertionError("Expected lobby payload within 20 messages.")
    }

    /**
     * Wartet, bis das [predicate] auf den gesammelten Log-Nachrichten erfüllt ist,
     * und gibt anschließend die zuletzt gelesenen Nachrichten zurück.
     *
     * Die Completion-Logs werden auf der Server-Coroutine erst nach dem Versand der
     * Antwort geschrieben, weshalb der Appender asynchron befüllt wird. Ohne dieses
     * Warten könnte die Prüfung unter Last fehlschlagen, weil der erwartete Eintrag
     * noch nicht angehängt wurde. Nach Ablauf von [timeoutMillis] werden die bis
     * dahin vorhandenen Nachrichten zurückgegeben, damit die folgenden Assertions
     * weiterhin eine aussagekräftige Fehlermeldung liefern.
     */
    private suspend fun awaitLoggedMessages(
        appender: ListAppender<ILoggingEvent>,
        timeoutMillis: Long = 5_000,
        predicate: (List<String>) -> Boolean,
    ): List<String> {
        val deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000
        while (true) {
            val messages = appender.list.map(ILoggingEvent::getFormattedMessage)
            if (predicate(messages) || System.nanoTime() >= deadlineNanos) {
                return messages
            }
            delay(25)
        }
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T)
        return value as T
    }

    private fun assertJoinLobbyResponse(
        lobbyCode: LobbyCode,
        payload: NetworkMessagePayload,
    ) {
        val response = assertIs<JoinLobbyResponse>(payload)
        assertEquals(lobbyCode, response.lobbyCode)
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

    private fun invokeBooleanHelper(
        name: String,
        state: GameState,
    ): Boolean {
        val method =
            Class.forName("at.aau.pulverfass.server.ApplicationKt").getDeclaredMethod(
                name,
                GameState::class.java,
            )
        method.isAccessible = true
        return method.invoke(null, state) as Boolean
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeMaxPlayerId(states: List<GameState>): Long {
        val method =
            Class.forName("at.aau.pulverfass.server.ApplicationKt").getDeclaredMethod(
                "maxPlayerId",
                List::class.java,
            )
        method.isAccessible = true
        return method.invoke(null, states) as Long
    }
}
