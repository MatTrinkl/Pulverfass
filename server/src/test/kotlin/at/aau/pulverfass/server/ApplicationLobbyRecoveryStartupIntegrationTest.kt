package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.map.ClasspathMapDefinitionRepository
import at.aau.pulverfass.server.persistence.JdbcLobbyPersistenceStore
import at.aau.pulverfass.server.persistence.JdbcLobbyReconnectSessionStore
import at.aau.pulverfass.server.persistence.PersistedLobbyRecoverySnapshot
import at.aau.pulverfass.server.session.Session
import at.aau.pulverfass.server.session.SessionContextRegistry
import at.aau.pulverfass.server.session.SessionReconnectContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.lobby.event.MatchEndReason
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.message.connection.event.GlobalPlayerCountEvent
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerCountUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(ExperimentalSerializationApi::class)
class ApplicationLobbyRecoveryStartupIntegrationTest {
    private lateinit var databaseConfig: TestDatabaseConfig
    private lateinit var store: JdbcLobbyPersistenceStore
    private lateinit var sessionStore: JdbcLobbyReconnectSessionStore
    private val mapDefinitionRepository = ClasspathMapDefinitionRepository.loadDefault()
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        }
    private var managedContainer: PostgreSQLContainer<*>? = null

    @BeforeAll
    fun setupDatabase() {
        databaseConfig = externalDatabaseConfig() ?: startManagedContainer()
        migrateDatabaseSchema(
            DatabaseRuntimeConfig(
                url = databaseConfig.jdbcUrl,
                user = databaseConfig.username,
                password = databaseConfig.password,
            ),
        )
        store = JdbcLobbyPersistenceStore(createDataSource())
        sessionStore = JdbcLobbyReconnectSessionStore(createDataSource())
    }

    @BeforeEach
    fun clearTables() {
        createDataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    TRUNCATE TABLE lobby_events, lobby_snapshots, lobby_reconnect_sessions
                    RESTART IDENTITY
                    """.trimIndent(),
                )
            }
        }
    }

    @AfterAll
    fun stopManagedContainer() {
        managedContainer?.stop()
    }

    @Test
    fun `startup restores open and finished lobbies and continues player ids`() =
        testApplication {
            val openLobbyCode = LobbyCode("OP11")
            val finishedLobbyCode = LobbyCode("FN22")
            val openHostToken = SessionToken("123e4567-e89b-12d3-a456-426614174510")
            val openGuestToken = SessionToken("123e4567-e89b-12d3-a456-426614174511")
            val finishedToken = SessionToken("123e4567-e89b-12d3-a456-426614174512")
            val openHostId = PlayerId(3)
            val openGuestId = PlayerId(7)

            persistRecoverySnapshot(
                GameState.initial(
                    lobbyCode = openLobbyCode,
                    mapDefinition = mapDefinitionRepository.defaultMapDefinition(),
                    players = listOf(openHostId, openGuestId),
                    playerDisplayNames =
                        mapOf(
                            openHostId to "Alice",
                            openGuestId to "Bob",
                        ),
                ).copy(
                    lobbyOwner = openHostId,
                    status = GameStatus.WAITING_FOR_PLAYERS,
                ),
            )
            persistRecoverySnapshot(
                GameState.initial(
                    lobbyCode = finishedLobbyCode,
                    mapDefinition = mapDefinitionRepository.defaultMapDefinition(),
                    players = listOf(PlayerId(9)),
                    playerDisplayNames = mapOf(PlayerId(9) to "Finished"),
                ).copy(
                    lobbyOwner = PlayerId(9),
                    status = GameStatus.FINISHED,
                    gameStarted = true,
                    closedReason = MatchEndReason.DECK_EMPTY.name,
                ),
            )
            sessionStore.upsertSession(
                Session(
                    sessionToken = openHostToken,
                    connectionId = null,
                    expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
                ),
                SessionReconnectContext(
                    playerId = openHostId,
                    lobbyCode = openLobbyCode,
                    playerDisplayName = "Alice",
                ),
            )
            sessionStore.upsertSession(
                Session(
                    sessionToken = openGuestToken,
                    connectionId = null,
                    expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
                ),
                SessionReconnectContext(
                    playerId = openGuestId,
                    lobbyCode = openLobbyCode,
                    playerDisplayName = "Bob",
                ),
            )
            sessionStore.upsertSession(
                Session(
                    sessionToken = finishedToken,
                    connectionId = null,
                    expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
                ),
                SessionReconnectContext(
                    playerId = PlayerId(9),
                    lobbyCode = finishedLobbyCode,
                    playerDisplayName = "Finished",
                ),
            )

            application {
                moduleWithLobbyRuntime(
                    network = ServerNetwork(),
                    runtimeConfig =
                        ServerRuntimeConfig(
                            database =
                                DatabaseRuntimeConfig(
                                    url = databaseConfig.jdbcUrl,
                                    user = databaseConfig.username,
                                    password = databaseConfig.password,
                                ),
                        ),
                )
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val session = client.webSocketSession("/ws")

            try {
                discardConnectionHandshake(session)
                session.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(JoinLobbyRequest(openLobbyCode, "Carol")),
                    ),
                )

                assertEquals(JoinLobbyResponse(openLobbyCode), receiveRelevantTestPayload(session))

                val firstPlayerEvent =
                    receiveRelevantTestPayload(session) as PlayerJoinedLobbyEvent
                val secondPlayerEvent =
                    receiveRelevantTestPayload(session) as PlayerJoinedLobbyEvent
                val thirdPlayerEvent =
                    receiveRelevantTestPayload(session) as PlayerJoinedLobbyEvent
                val joinedIds =
                    listOf(firstPlayerEvent, secondPlayerEvent, thirdPlayerEvent)
                        .map(PlayerJoinedLobbyEvent::playerId)

                assertEquals(listOf(openHostId, openGuestId, PlayerId(10)), joinedIds)
                assertEquals(
                    setOf(openLobbyCode, finishedLobbyCode),
                    store.findLobbyCodesWithPersistedState(),
                )
                assertEquals(
                    SessionReconnectContext(
                        playerId = PlayerId(9),
                        lobbyCode = finishedLobbyCode,
                        playerDisplayName = "Finished",
                    ),
                    sessionStore.loadContext(
                        finishedToken,
                    ),
                )
            } finally {
                session.close()
            }
        }

    @Test
    fun `startup restores persisted reconnect sessions for open lobbies`() =
        testApplication {
            val lobbyCode = LobbyCode("RC14")
            val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174540")
            val playerId = PlayerId(5)

            persistRecoverySnapshot(
                GameState.initial(
                    lobbyCode = lobbyCode,
                    mapDefinition = mapDefinitionRepository.defaultMapDefinition(),
                    players = listOf(playerId),
                    playerDisplayNames = mapOf(playerId to "Alice"),
                ).copy(
                    lobbyOwner = playerId,
                    status = GameStatus.WAITING_FOR_PLAYERS,
                ),
            )
            sessionStore.upsertSession(
                Session(
                    sessionToken = sessionToken,
                    connectionId = null,
                    expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
                ),
                SessionReconnectContext(
                    playerId = playerId,
                    lobbyCode = lobbyCode,
                    playerDisplayName = "Alice",
                ),
            )

            application {
                moduleWithLobbyRuntime(
                    network = ServerNetwork(),
                    runtimeConfig =
                        ServerRuntimeConfig(
                            database =
                                DatabaseRuntimeConfig(
                                    url = databaseConfig.jdbcUrl,
                                    user = databaseConfig.username,
                                    password = databaseConfig.password,
                                ),
                        ),
                )
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val session = client.webSocketSession("/ws")

            try {
                discardConnectionHandshake(session)
                session.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(sessionToken)),
                    ),
                )

                assertEquals(
                    ReconnectResponse(
                        success = true,
                        playerId = playerId,
                        lobbyCode = lobbyCode,
                        playerDisplayName = "Alice",
                    ),
                    receiveTestPayloadOfType<ReconnectResponse>(session),
                )
            } finally {
                session.close()
            }
        }

    @Test
    fun `runtime reconnect falls back to in memory lobby context`() =
        testApplication {
            val lobbyCode = LobbyCode("RC15")
            val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174541")
            val playerId = PlayerId(6)

            persistRecoverySnapshot(
                GameState.initial(
                    lobbyCode = lobbyCode,
                    mapDefinition = mapDefinitionRepository.defaultMapDefinition(),
                    players = listOf(playerId),
                    playerDisplayNames = mapOf(playerId to "Alice"),
                ).copy(
                    lobbyOwner = playerId,
                    status = GameStatus.WAITING_FOR_PLAYERS,
                ),
            )
            sessionStore.upsertSession(
                Session(
                    sessionToken = sessionToken,
                    connectionId = null,
                    expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
                ),
                SessionReconnectContext(
                    playerId = playerId,
                    lobbyCode = lobbyCode,
                    playerDisplayName = "Alice",
                ),
            )

            application {
                moduleWithLobbyRuntime(
                    network = ServerNetwork(),
                    runtimeConfig =
                        ServerRuntimeConfig(
                            database =
                                DatabaseRuntimeConfig(
                                    url = databaseConfig.jdbcUrl,
                                    user = databaseConfig.username,
                                    password = databaseConfig.password,
                                ),
                        ),
                )
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val firstSession = client.webSocketSession("/ws")

            try {
                discardConnectionHandshake(firstSession)
                firstSession.send(
                    Frame.Binary(
                        fin = true,
                        data = MessageCodec.encode(ReconnectRequest(sessionToken)),
                    ),
                )

                assertEquals(
                    ReconnectResponse(
                        success = true,
                        playerId = playerId,
                        lobbyCode = lobbyCode,
                        playerDisplayName = "Alice",
                    ),
                    receiveTestPayloadOfType<ReconnectResponse>(firstSession),
                )

                sessionStore.deleteSession(sessionToken)
                firstSession.close()

                val reconnectingSession = client.webSocketSession("/ws")
                try {
                    discardConnectionHandshake(reconnectingSession)
                    reconnectingSession.send(
                        Frame.Binary(
                            fin = true,
                            data = MessageCodec.encode(ReconnectRequest(sessionToken)),
                        ),
                    )

                    assertEquals(
                        ReconnectResponse(
                            success = true,
                            playerId = playerId,
                            lobbyCode = lobbyCode,
                            playerDisplayName = "Alice",
                        ),
                        receiveTestPayloadOfType<ReconnectResponse>(reconnectingSession),
                    )
                } finally {
                    reconnectingSession.close()
                }
            } finally {
                firstSession.close()
            }
        }

    @Test
    fun `persistReconnectSessionIfPossible persists only sessions with assigned player`() {
        val network = ServerNetwork()
        val sessionContextRegistry = SessionContextRegistry()
        val sessionTokenWithoutPlayer = SessionToken("123e4567-e89b-12d3-a456-426614174520")
        val sessionTokenWithPlayer = SessionToken("123e4567-e89b-12d3-a456-426614174521")
        val lobbyCode = LobbyCode("PS11")
        val playerId = PlayerId(4)

        network.sessionManager.restoreDetachedSession(
            sessionTokenWithoutPlayer,
            System.currentTimeMillis() + 60_000,
        )
        sessionContextRegistry.updateLobbyContext(
            sessionTokenWithoutPlayer,
            lobbyCode,
            "NoPlayer",
        )
        persistReconnectSessionIfPossible(
            network = network,
            sessionStore = sessionStore,
            sessionContextRegistry = sessionContextRegistry,
            sessionToken = sessionTokenWithoutPlayer,
        )
        assertNull(sessionStore.loadSession(sessionTokenWithoutPlayer))

        network.sessionManager.restoreDetachedSession(
            sessionTokenWithPlayer,
            System.currentTimeMillis() + 60_000,
        )
        sessionContextRegistry.assignPlayer(sessionTokenWithPlayer, playerId)
        sessionContextRegistry.updateLobbyContext(
            sessionTokenWithPlayer,
            lobbyCode,
            "Alice",
        )
        persistReconnectSessionIfPossible(
            network = network,
            sessionStore = sessionStore,
            sessionContextRegistry = sessionContextRegistry,
            sessionToken = sessionTokenWithPlayer,
        )

        val persistedSession = sessionStore.loadSession(sessionTokenWithPlayer)
        assertEquals(playerId, persistedSession?.context?.playerId)
        assertEquals(lobbyCode, persistedSession?.context?.lobbyCode)
        assertEquals("Alice", persistedSession?.context?.playerDisplayName)
    }

    @Test
    fun `cleanupTerminalLobbyState removes runtime session context and persisted lobby state`() {
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val lobbyManager = LobbyManager(scope = scope)
            val lobbyCode = LobbyCode("CL11")
            val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174530")
            val sessionContextRegistry = SessionContextRegistry()

            try {
                lobbyManager.createLobby(lobbyCode)
                sessionContextRegistry.assignPlayer(sessionToken, PlayerId(10))
                sessionContextRegistry.updateLobbyContext(sessionToken, lobbyCode, "Cleanup")
                store.appendSnapshot(
                    lobbyCode = lobbyCode,
                    stateVersion = 1,
                    turnCount = 0,
                    snapshotJson = JsonPrimitive("""{"state":"persisted"}"""),
                )

                cleanupTerminalLobbyState(
                    lobbyCode = lobbyCode,
                    lobbyManager = lobbyManager,
                    sessionContextRegistry = sessionContextRegistry,
                    recoveryStore = store,
                )

                assertNull(lobbyManager.getLobby(lobbyCode))
                assertNull(sessionContextRegistry.contextFor(sessionToken)?.lobbyCode)
                assertTrue(lobbyCode !in store.findLobbyCodesWithPersistedState())
            } finally {
                scope.cancel()
            }
        }
    }

    private fun persistRecoverySnapshot(state: GameState) {
        store.appendSnapshot(
            lobbyCode = state.lobbyCode,
            stateVersion = state.stateVersion,
            turnCount = state.resolvedTurnState?.turnCount ?: state.turnNumber,
            snapshotJson =
                json.encodeToJsonElement(
                    PersistedLobbyRecoverySnapshot.serializer(),
                    PersistedLobbyRecoverySnapshot.fromGameState(state),
                ),
        )
    }

    private suspend fun discardConnectionHandshake(session: DefaultClientWebSocketSession) {
        repeat(20) {
            val payload = receiveRawTestPayload(session)
            if (payload is ConnectionResponse) return
        }
        error("Expected ConnectionResponse within 20 messages.")
    }

    private suspend fun receiveRelevantTestPayload(session: DefaultClientWebSocketSession): Any {
        repeat(20) {
            val payload = receiveRawTestPayload(session)
            if (
                payload !is ConnectionResponse &&
                payload !is GameStateDeltaEvent &&
                payload !is GameStateSnapshotBroadcast &&
                payload !is TurnStateUpdatedEvent &&
                payload !is PlayerCountUpdateEvent &&
                payload !is GlobalPlayerCountEvent &&
                payload !is PlayerConnectionLostEvent
            ) {
                return payload
            }
        }
        error("Expected relevant payload within 20 messages.")
    }

    /**
     * Liest Broadcasts reihenfolgeunabhängig, bis die erwartete Payload eintrifft.
     *
     * Reconnects dürfen Status- und Roster-Events vor oder nach ihrer Antwort senden.
     */
    private suspend inline fun <reified T> receiveTestPayloadOfType(
        session: DefaultClientWebSocketSession,
        maxMessages: Int = 20,
    ): T {
        repeat(maxMessages) {
            val payload = receiveRawTestPayload(session)
            if (payload is T) {
                return payload
            }
        }
        error("Expected ${T::class.java.simpleName} within $maxMessages messages.")
    }

    private suspend fun receiveRawTestPayload(session: DefaultClientWebSocketSession): Any =
        MessageCodec.decodePayload(receiveFrameBytes(session))

    private suspend fun receiveFrameBytes(session: DefaultClientWebSocketSession): ByteArray =
        (session.incoming.receive() as Frame.Binary).data

    private fun createDataSource() =
        PGSimpleDataSource().apply {
            setURL(databaseConfig.jdbcUrl)
            user = databaseConfig.username
            password = databaseConfig.password
        }

    private fun externalDatabaseConfig(): TestDatabaseConfig? {
        val jdbcUrl = System.getenv("PULVERFASS_TEST_DB_URL")?.trim().orEmpty()
        val username = System.getenv("PULVERFASS_TEST_DB_USER")?.trim().orEmpty()
        val password = System.getenv("PULVERFASS_TEST_DB_PASSWORD")?.trim().orEmpty()
        if (jdbcUrl.isEmpty() && username.isEmpty() && password.isEmpty()) {
            return null
        }
        require(jdbcUrl.isNotEmpty()) {
            "PULVERFASS_TEST_DB_URL muss gesetzt sein, wenn eine externe Test-DB genutzt wird."
        }
        require(username.isNotEmpty()) {
            "PULVERFASS_TEST_DB_USER muss gesetzt sein, wenn eine externe Test-DB genutzt wird."
        }
        return TestDatabaseConfig(
            jdbcUrl = jdbcUrl,
            username = username,
            password = password,
        )
    }

    private fun startManagedContainer(): TestDatabaseConfig {
        assumeDockerAvailableForTestcontainers()
        val container = PostgreSQLContainer("postgres:17-alpine")
        container.start()
        managedContainer = container
        return TestDatabaseConfig(
            jdbcUrl = container.jdbcUrl,
            username = container.username,
            password = container.password,
        )
    }

    private data class TestDatabaseConfig(
        val jdbcUrl: String,
        val username: String,
        val password: String,
    )
}
