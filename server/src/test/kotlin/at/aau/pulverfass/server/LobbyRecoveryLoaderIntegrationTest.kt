package at.aau.pulverfass.server

import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.map.ClasspathMapDefinitionRepository
import at.aau.pulverfass.server.persistence.DatabaseBackedLobbyPersistenceGateway
import at.aau.pulverfass.server.persistence.JdbcLobbyPersistenceStore
import at.aau.pulverfass.server.persistence.LobbyRecoveryLoader
import at.aau.pulverfass.server.routing.PublicGameStateBuilder
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.normalizePlayerDisplayNameOrFallback
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LobbyRecoveryLoaderIntegrationTest {
    private lateinit var databaseConfig: TestDatabaseConfig
    private lateinit var store: JdbcLobbyPersistenceStore
    private lateinit var gateway: DatabaseBackedLobbyPersistenceGateway
    private val mapDefinitionRepository = ClasspathMapDefinitionRepository.loadDefault()
    private val reducer = DefaultLobbyEventReducer()
    private val snapshotBuilder = PublicGameStateBuilder()
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
        gateway = DatabaseBackedLobbyPersistenceGateway(store = store)
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
    fun `restore returns null when no persisted state exists`() {
        val restored = createRecoveryLoader().restoreLobby(LobbyCode("1318"))

        assertNull(restored)
    }

    @Test
    fun `restore rebuilds lobby from latest snapshot and newer events`() {
        val lobbyCode = LobbyCode("1320")
        val hostId = PlayerId(1)
        val playerTwoId = PlayerId(2)
        val playerThreeId = PlayerId(3)
        val players = listOf(hostId, playerTwoId, playerThreeId)

        var currentState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = mapDefinitionRepository.defaultMapDefinition(),
                players = players,
                playerDisplayNames =
                    mapOf(
                        hostId to "Host",
                        playerTwoId to "Player 2",
                        playerThreeId to "Player 3",
                    ),
            ).copy(
                lobbyOwner = hostId,
                status = GameStatus.RUNNING,
                gameStarted = true,
            )

        repeat(5) { index ->
            val (event, nextState) = advanceState(currentState)
            runBlocking {
                gateway.onLobbyEventAccepted(
                    event = event,
                    previousState = currentState,
                    currentState = nextState,
                )
            }
            currentState = nextState

            if (index == 3) {
                runBlocking {
                    gateway.onSnapshotBroadcast(
                        currentState = currentState,
                        payload = snapshotBuilder.buildSnapshotBroadcast(currentState),
                    )
                }
            }
        }

        val persistedSnapshot = store.loadLatestSnapshot(lobbyCode)
        assertNotNull(persistedSnapshot)
        assertEquals(4L, persistedSnapshot!!.stateVersion)
        assertEquals(
            listOf(5L),
            store.loadEventsAfter(
                lobbyCode = lobbyCode,
                stateVersionExclusive = persistedSnapshot.stateVersion,
            ).map { it.stateVersion },
        )

        val restoredState = restoreLobbyState(lobbyCode)
        assertNotNull(restoredState)
        assertEquals(
            normalizeRecoveredState(currentState),
            normalizeRecoveredState(restoredState!!),
        )
    }

    @Test
    fun `restore rebuilds lobby from persisted events without snapshot`() {
        val lobbyCode = LobbyCode("1321")
        val hostId = PlayerId(1)
        val guestId = PlayerId(2)

        val currentState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = mapDefinitionRepository.defaultMapDefinition(),
            )
        val expectedState =
            reducer
                .apply(
                    currentState,
                    PlayerJoined(lobbyCode, hostId, "Host"),
                    context = null,
                ).let { stateAfterHost ->
                    reducer.apply(
                        stateAfterHost,
                        PlayerJoined(lobbyCode, guestId, "Guest"),
                        context = null,
                    )
                }
        store.appendEvent(
            lobbyCode = lobbyCode,
            stateVersion = 1,
            turnCount = 0,
            eventType = "player_joined",
            eventJson =
                buildJsonObject {
                    put("lobbyCode", lobbyCode.value)
                    put("playerId", hostId.value)
                    put("playerDisplayName", "Host")
                },
        )
        store.appendEvent(
            lobbyCode = lobbyCode,
            stateVersion = 2,
            turnCount = 0,
            eventType = "player_joined",
            eventJson =
                buildJsonObject {
                    put("lobbyCode", lobbyCode.value)
                    put("playerId", guestId.value)
                    put("playerDisplayName", "Guest")
                },
        )

        val restoredState = createRecoveryLoader().restoreLobby(lobbyCode)

        assertNotNull(restoredState)
        assertEquals(
            normalizeRecoveredState(expectedState),
            normalizeRecoveredState(restoredState!!),
        )
    }

    @Test
    fun `restore rejects persisted event sequences with gaps`() {
        val lobbyCode = LobbyCode("1322")
        store.appendEvent(
            lobbyCode = lobbyCode,
            stateVersion = 2,
            turnCount = 0,
            eventType = "lobby_created",
            eventJson =
                buildJsonObject {
                    put("lobbyCode", lobbyCode.value)
                },
        )

        assertThrows(IllegalStateException::class.java) {
            createRecoveryLoader().restoreLobby(lobbyCode)
        }
    }

    private fun advanceState(state: GameState): Pair<TurnStateUpdatedEvent, GameState> {
        val currentTurnState =
            state.resolvedTurnState
                ?: error("TurnState muss für Recovery-Tests vorhanden sein.")
        val nextTurnState =
            TurnStateMachine.advance(
                turnState = currentTurnState,
                turnOrder = state.turnOrder,
            )
        val event =
            TurnStateUpdatedEvent(
                lobbyCode = state.lobbyCode,
                activePlayerId = nextTurnState.activePlayerId,
                turnPhase = nextTurnState.turnPhase,
                turnCount = nextTurnState.turnCount,
                startPlayerId = nextTurnState.startPlayerId,
                isPaused = nextTurnState.isPaused,
                pauseReason = nextTurnState.pauseReason,
                pausedPlayerId = nextTurnState.pausedPlayerId,
            )
        val updatedState = reducer.apply(state, event, context = null)
        return event to updatedState
    }

    private fun restoreLobbyState(lobbyCode: LobbyCode): GameState? {
        val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lobbyManager =
            LobbyManager(
                scope = recoveryScope,
                initialStateFactory = { code ->
                    GameState.initial(
                        lobbyCode = code,
                        mapDefinition = mapDefinitionRepository.defaultMapDefinition(),
                    )
                },
            )

        return try {
            createRecoveryLoader().restoreAllInto(lobbyManager)
            lobbyManager.getLobby(lobbyCode)?.currentState()
        } finally {
            runBlocking {
                lobbyManager.shutdownAll()
            }
            recoveryScope.cancel()
        }
    }

    private fun normalizeRecoveredState(state: GameState): GameState =
        state.copy(
            lastEventContext = null,
            playerDisplayNames =
                state.playerDisplayNames.mapValues { (_, playerDisplayName) ->
                    normalizePlayerDisplayNameOrFallback(playerDisplayName)
                },
        )

    private fun createRecoveryLoader() =
        LobbyRecoveryLoader(
            store = JdbcLobbyPersistenceStore(createDataSource()),
            mapDefinitionRepository = mapDefinitionRepository,
        )

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
