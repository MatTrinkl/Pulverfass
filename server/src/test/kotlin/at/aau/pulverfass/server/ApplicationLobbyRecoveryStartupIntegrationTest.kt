package at.aau.pulverfass.server

import at.aau.pulverfass.server.map.ClasspathMapDefinitionRepository
import at.aau.pulverfass.server.persistence.JdbcLobbyPersistenceStore
import at.aau.pulverfass.server.persistence.JdbcLobbyReconnectSessionStore
import at.aau.pulverfass.server.persistence.PersistedLobbyRecoverySnapshot
import at.aau.pulverfass.server.session.Session
import at.aau.pulverfass.server.session.SessionReconnectContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `startup restores open lobbies clears finished lobby context and continues player ids`() =
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
                assertEquals(setOf(openLobbyCode), store.findLobbyCodesWithPersistedState())
                assertEquals(
                    SessionReconnectContext(playerId = PlayerId(9)),
                    sessionStore.loadContext(
                        finishedToken,
                    ),
                )
            } finally {
                session.close()
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
        assertTrue(receiveRawTestPayload(session) is ConnectionResponse)
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
