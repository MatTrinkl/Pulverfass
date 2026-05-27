package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.persistence.DatabaseBackedLobbyPersistenceGateway
import at.aau.pulverfass.server.persistence.JdbcLobbyPersistenceStore
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.ConcurrentHashMap

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LobbyPersistenceHooksIntegrationTest {
    private lateinit var databaseConfig: TestDatabaseConfig
    private lateinit var store: JdbcLobbyPersistenceStore
    private lateinit var gateway: DatabaseBackedLobbyPersistenceGateway
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
        val dataSource = createDataSource()
        store = JdbcLobbyPersistenceStore(dataSource)
        gateway =
            DatabaseBackedLobbyPersistenceGateway(
                store = store,
            )
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
    fun closeResources() {
        if (::gateway.isInitialized) {
            gateway.close()
        }
        managedContainer?.stop()
    }

    @Test
    fun `turn advance persists accepted event row`() =
        testApplication {
            val fixture = createRunningLobbyFixture("PS11")
            application {
                module(fixture.network)
            }
            fixture.start()

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val hostSession = fixture.connectPlayer(client, fixture.hostId)
                    hostSession.send(
                        Frame.Binary(
                            fin = true,
                            data =
                                MessageCodec.encode(
                                    TurnAdvanceRequest(
                                        lobbyCode = fixture.lobbyCode,
                                        playerId = fixture.hostId,
                                        expectedPhase = TurnPhase.REINFORCEMENTS,
                                    ),
                                ),
                        ),
                    )

                    assertEquals(
                        TurnAdvanceResponse(fixture.lobbyCode),
                        receiveRelevantTestPayload(hostSession, skipGameSync = true),
                    )
                    assertTrue(
                        receiveRelevantTestPayload(hostSession, skipGameSync = true)
                            is PhaseBoundaryEvent,
                    )
                    receiveRelevantTestPayload(hostSession, skipGameSync = true)

                    val persistedEvents = store.listEvents(fixture.lobbyCode)
                    assertEquals(listOf("turn_state_updated"), persistedEvents.map { it.eventType })
                    assertEquals(listOf(1L), persistedEvents.map { it.stateVersion })
                    assertEquals(listOf(1), persistedEvents.map { it.turnCount })

                    hostSession.close()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun `full turn change persists snapshot row with determinism and turn state`() =
        testApplication {
            val fixture = createRunningLobbyFixture("PS22")
            application {
                module(fixture.network)
            }
            fixture.start()

            val client =
                createClient {
                    install(WebSockets)
                }

            try {
                coroutineScope {
                    val hostSession = fixture.connectPlayer(client, fixture.hostId)

                    advanceTurn(
                        hostSession,
                        fixture.lobbyCode,
                        fixture.hostId,
                        TurnPhase.REINFORCEMENTS,
                    )
                    val attackAutoEnded =
                        consumeOptionalAttackAutoEnd(
                            session = hostSession,
                            lobbyCode = fixture.lobbyCode,
                            playerId = fixture.hostId,
                        )
                    if (!attackAutoEnded) {
                        advanceTurn(
                            hostSession,
                            fixture.lobbyCode,
                            fixture.hostId,
                            TurnPhase.ATTACK,
                        )
                    }
                    advanceTurn(
                        hostSession,
                        fixture.lobbyCode,
                        fixture.hostId,
                        TurnPhase.FORTIFY,
                    )
                    advanceTurn(
                        hostSession,
                        fixture.lobbyCode,
                        fixture.hostId,
                        TurnPhase.DRAW_CARD,
                    )

                    val snapshotPayload =
                        receiveRelevantTestPayload(
                            hostSession,
                        )
                    assertTrue(snapshotPayload is GameStateSnapshotBroadcast)

                    val persistedSnapshots =
                        awaitSnapshots(
                            lobbyCode = fixture.lobbyCode,
                            expectedCount = 1,
                        )
                    assertEquals(1, persistedSnapshots.size)
                    val snapshot = persistedSnapshots.single()
                    assertEquals(5L, snapshot.stateVersion)
                    assertEquals(1, snapshot.turnCount)

                    val snapshotJson = Json.parseToJsonElement(snapshot.snapshotJson).jsonObject
                    val determinism = snapshotJson.getValue("determinism").jsonObject
                    val turnState = snapshotJson.getValue("turnState").jsonObject
                    assertEquals(
                        defaultMapDefinition().mapHash,
                        determinism.getValue("mapHash").toString().trim('"'),
                    )
                    assertEquals(
                        defaultMapDefinition().schemaVersion.toString(),
                        determinism.getValue("schemaVersion").toString(),
                    )
                    assertEquals("2", turnState.getValue("activePlayerId").toString())
                    assertEquals("1", turnState.getValue("turnCount").toString())

                    hostSession.close()
                }
            } finally {
                fixture.stop()
            }
        }

    private fun createRunningLobbyFixture(code: String): LobbyPersistenceFixture {
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
        val routingService =
            MainServerLobbyRoutingService(
                network = network,
                router = router,
                lobbyManager = lobbyManager,
                playerIdResolver = { connectionId -> playersByConnection[connectionId] },
                connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
                persistenceCallbacks = gateway,
            )
        val lobbyCode = LobbyCode(code)
        val hostId = PlayerId(1)
        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = defaultMapDefinition(),
                players = listOf(hostId, PlayerId(2), PlayerId(3)),
                playerDisplayNames =
                    mapOf(
                        hostId to "Host",
                        PlayerId(2) to "Player 2",
                        PlayerId(3) to "Player 3",
                    ),
            ).copy(
                lobbyOwner = hostId,
                status = GameStatus.RUNNING,
                gameStarted = true,
            )
        lobbyManager.createLobby(lobbyCode = lobbyCode, initialState = initialState)
        return LobbyPersistenceFixture(
            network = network,
            serverScope = serverScope,
            lobbyManager = lobbyManager,
            routingService = routingService,
            playersByConnection = playersByConnection,
            connectionsByPlayer = connectionsByPlayer,
            lobbyCode = lobbyCode,
            hostId = hostId,
        )
    }

    private suspend fun advanceTurn(
        session: DefaultClientWebSocketSession,
        lobbyCode: LobbyCode,
        playerId: PlayerId,
        expectedPhase: TurnPhase,
    ) {
        session.send(
            Frame.Binary(
                fin = true,
                data =
                    MessageCodec.encode(
                        TurnAdvanceRequest(
                            lobbyCode = lobbyCode,
                            playerId = playerId,
                            expectedPhase = expectedPhase,
                        ),
                    ),
            ),
        )
        assertEquals(
            TurnAdvanceResponse(lobbyCode),
            receiveRelevantTestPayload(session, skipGameSync = true),
        )
        assertTrue(receiveRelevantTestPayload(session, skipGameSync = true) is PhaseBoundaryEvent)
        receiveRelevantTestPayload(session, skipGameSync = true)
    }

    private suspend fun consumeOptionalAttackAutoEnd(
        session: DefaultClientWebSocketSession,
        lobbyCode: LobbyCode,
        playerId: PlayerId,
    ): Boolean {
        val payload = receiveRelevantTestPayloadOrNull(session, skipGameSync = true) ?: return false
        val boundary =
            payload as? PhaseBoundaryEvent
                ?: throw AssertionError(
                    "Expected optional attack auto-end boundary, but received: $payload",
                )
        assertEquals(lobbyCode, boundary.lobbyCode)
        assertEquals(TurnPhase.ATTACK, boundary.previousPhase)
        assertEquals(TurnPhase.FORTIFY, boundary.nextPhase)
        assertEquals(playerId, boundary.activePlayerId)
        assertTrue(
            receiveRelevantTestPayload(session, skipGameSync = true) is TurnStateUpdatedEvent,
        )
        return true
    }

    private suspend fun awaitSnapshots(
        lobbyCode: LobbyCode,
        expectedCount: Int,
    ) = withTimeout(5_000) {
        var snapshots = store.listSnapshots(lobbyCode)
        while (snapshots.size < expectedCount) {
            delay(25)
            snapshots = store.listSnapshots(lobbyCode)
        }
        snapshots
    }

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

    private fun defaultMapDefinition() = MapConfigLoader.loadDefault()

    private data class TestDatabaseConfig(
        val jdbcUrl: String,
        val username: String,
        val password: String,
    )

    private data class LobbyPersistenceFixture(
        val network: ServerNetwork,
        val serverScope: CoroutineScope,
        val lobbyManager: LobbyManager,
        val routingService: MainServerLobbyRoutingService,
        val playersByConnection: ConcurrentHashMap<ConnectionId, PlayerId>,
        val connectionsByPlayer: ConcurrentHashMap<PlayerId, ConnectionId>,
        val lobbyCode: LobbyCode,
        val hostId: PlayerId,
    ) {
        fun start() {
            routingService.start(serverScope)
        }

        suspend fun stop() {
            routingService.stop()
            lobbyManager.shutdownAll()
            serverScope.cancel()
        }

        suspend fun connectPlayer(
            client: io.ktor.client.HttpClient,
            playerId: PlayerId,
        ): DefaultClientWebSocketSession {
            val session = client.webSocketSession("/ws")
            val sessionToken = receiveTestConnectionToken(session)
            val connectionId = awaitTestConnectionId(network, sessionToken)
            playersByConnection[connectionId] = playerId
            connectionsByPlayer[playerId] = connectionId
            return session
        }
    }
}
