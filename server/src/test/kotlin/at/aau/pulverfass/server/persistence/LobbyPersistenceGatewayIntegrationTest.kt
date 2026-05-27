package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.server.DatabaseRuntimeConfig
import at.aau.pulverfass.server.assumeDockerAvailableForTestcontainers
import at.aau.pulverfass.server.migrateDatabaseSchema
import at.aau.pulverfass.server.routing.PublicGameStateBuilder
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsSetEvent
import at.aau.pulverfass.shared.lobby.event.PlayerCardsRemovedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.PlayerKicked
import at.aau.pulverfass.shared.lobby.event.PlayerLeft
import at.aau.pulverfass.shared.lobby.event.StartPlayerConfigured
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TimeoutTriggered
import at.aau.pulverfass.shared.lobby.event.TurnEnded
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.MapConfigLoader
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.atomic.AtomicBoolean

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LobbyPersistenceGatewayIntegrationTest {
    private lateinit var store: JdbcLobbyPersistenceStore
    private lateinit var databaseConfig: TestDatabaseConfig
    private val mapDefinition = MapConfigLoader.loadDefault()
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
    fun `gateway persists all supported event payload types`() =
        runBlocking {
            val lobbyCode = LobbyCode("PG11")
            val hostId = PlayerId(1)
            val guestId = PlayerId(2)
            val thirdPlayerId = PlayerId(3)
            val gateway = DatabaseBackedLobbyPersistenceGateway(store = store)

            val events =
                listOf(
                    LobbyCreated(lobbyCode),
                    AttackResolvedEvent(
                        lobbyCode = lobbyCode,
                        attackerPlayerId = hostId,
                        defenderPlayerId = guestId,
                        fromTerritoryId = TerritoryId("alpha"),
                        toTerritoryId = TerritoryId("beta"),
                        attackTroops = 3,
                        sourceTroopsBefore = 5,
                        targetTroopsBefore = 2,
                        requestedAttackDice = 3,
                        attackDice = 3,
                        defendDice = 2,
                        attackerRolls = listOf(6, 5, 4),
                        defenderRolls = listOf(3, 2),
                        rngTrace = listOf(6, 4, 5, 2, 3),
                        rngStateBefore = 11L,
                        rngStateAfter = 12L,
                        attackerLosses = 0,
                        defenderLosses = 2,
                        attackerRemaining = 5,
                        defenderRemaining = 0,
                        occupyingTroopCount = 3,
                        minOccupyingTroops = 3,
                    ),
                    PlayerEliminatedEvent(
                        lobbyCode = lobbyCode,
                        playerId = guestId,
                        eliminatedByPlayerId = hostId,
                        stateVersion = 2L,
                    ),
                    CardSetTradedInEvent(
                        lobbyCode = lobbyCode,
                        playerId = hostId,
                        cardIds = listOf(CardId("ca"), CardId("cb"), CardId("cc")),
                        value = 8,
                        tradeIndex = 2,
                    ),
                    PendingReinforcementsSetEvent(
                        lobbyCode = lobbyCode,
                        playerId = hostId,
                        amount = 7,
                    ),
                    PendingReinforcementsChangedEvent(
                        lobbyCode = lobbyCode,
                        playerId = hostId,
                        delta = -2,
                    ),
                    PlayerCardsRemovedEvent(
                        lobbyCode = lobbyCode,
                        playerId = hostId,
                        cardIds = listOf(CardId("ca"), CardId("cb")),
                    ),
                    LobbyClosed(lobbyCode, "done"),
                    PlayerJoined(lobbyCode, thirdPlayerId, "Third"),
                    PlayerLeft(lobbyCode, thirdPlayerId, "quit"),
                    PlayerKicked(lobbyCode, thirdPlayerId, hostId),
                    StartPlayerConfigured(lobbyCode, hostId, hostId),
                    GameStarted(lobbyCode, 77L),
                    InvalidActionDetected(lobbyCode, null, "invalid"),
                    SystemTick(lobbyCode, 19L),
                    TerritoryOwnerChangedEvent(
                        lobbyCode = lobbyCode,
                        territoryId = TerritoryId("gamma"),
                        ownerId = null,
                        stateVersion = 15L,
                    ),
                    TerritoryTroopsChangedEvent(
                        lobbyCode = lobbyCode,
                        territoryId = TerritoryId("gamma"),
                        troopCount = 9,
                        stateVersion = 16L,
                    ),
                    TimeoutTriggered(lobbyCode, "attack", 5_000L),
                    TurnEnded(lobbyCode, hostId),
                    TurnStateUpdatedEvent(
                        lobbyCode = lobbyCode,
                        activePlayerId = guestId,
                        turnPhase = TurnPhase.FORTIFY,
                        turnCount = 2,
                        startPlayerId = hostId,
                        isPaused = true,
                        pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                        pausedPlayerId = guestId,
                    ),
                )

            events.forEachIndexed { index, event ->
                val currentState = runningState(lobbyCode, stateVersion = (index + 1).toLong())
                gateway.onLobbyEventAccepted(
                    event = event,
                    previousState = currentState.copy(stateVersion = currentState.stateVersion - 1),
                    currentState = currentState,
                )
            }

            val persisted = store.listEvents(lobbyCode)
            assertEquals(events.size, persisted.size)
            assertEquals(
                listOf(
                    "lobby_created",
                    "attack_resolved",
                    "player_eliminated",
                    "card_set_traded_in",
                    "pending_reinforcements_set",
                    "pending_reinforcements_changed",
                    "player_cards_removed",
                    "lobby_closed",
                    "player_joined",
                    "player_left",
                    "player_kicked",
                    "start_player_configured",
                    "game_started",
                    "invalid_action_detected",
                    "system_tick",
                    "territory_owner_changed",
                    "territory_troops_changed",
                    "timeout_triggered",
                    "turn_ended",
                    "turn_state_updated",
                ),
                persisted.map(PersistedLobbyEventRecord::eventType),
            )

            val attackJson =
                Json.parseToJsonElement(
                    persisted.first { it.eventType == "attack_resolved" }.eventJson,
                ).jsonObject
            assertEquals("alpha", attackJson.getValue("fromTerritoryId").jsonPrimitive.content)
            assertEquals("3", attackJson.getValue("occupyingTroopCount").jsonPrimitive.content)

            val tradeJson =
                Json.parseToJsonElement(
                    persisted.first { it.eventType == "card_set_traded_in" }.eventJson,
                ).jsonObject
            assertEquals(
                """["ca","cb","cc"]""",
                tradeJson.getValue("cardIds").toString(),
            )

            val invalidJson =
                Json.parseToJsonElement(
                    persisted.first { it.eventType == "invalid_action_detected" }.eventJson,
                ).jsonObject
            assertNull(invalidJson["playerId"]?.jsonPrimitive?.contentOrNull)

            val turnStateJson =
                Json.parseToJsonElement(
                    persisted.first { it.eventType == "turn_state_updated" }.eventJson,
                ).jsonObject
            assertEquals("FORTIFY", turnStateJson.getValue("turnPhase").jsonPrimitive.content)
            assertEquals(
                "WAITING_FOR_PLAYER",
                turnStateJson.getValue("pauseReason").jsonPrimitive.content,
            )
        }

    @Test
    fun `gateway persists snapshots and tracks readiness on failures`() =
        runBlocking {
            val lobbyCode = LobbyCode("PG12")
            val gateway = DatabaseBackedLobbyPersistenceGateway(store = store)
            val currentState = runningState(lobbyCode, stateVersion = 4L)

            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    gateway.onSnapshotBroadcast(
                        snapshotBuilder.buildSnapshotBroadcast(currentState),
                    )
                }
            }

            gateway.onPhaseBoundaryBroadcast(
                snapshotBuilder.buildSnapshotBroadcast(currentState).turnState.let { turnState ->
                    at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent(
                        lobbyCode = lobbyCode,
                        previousPhase = TurnPhase.ATTACK,
                        nextPhase = turnState.turnPhase,
                        activePlayerId = turnState.activePlayerId,
                        turnCount = turnState.turnCount,
                        stateVersion = currentState.stateVersion,
                    )
                },
            )

            gateway.onSnapshotBroadcast(
                currentState = currentState,
                payload = snapshotBuilder.buildSnapshotBroadcast(currentState),
            )

            val snapshots = store.listSnapshots(lobbyCode)
            assertEquals(1, snapshots.size)
            assertTrue(snapshots.single().snapshotJson.contains("determinism"))
            assertEquals("UP", gateway.readiness().state.name)

            val brokenGateway =
                DatabaseBackedLobbyPersistenceGateway(
                    store =
                        JdbcLobbyPersistenceStore(
                            dataSource =
                                PGSimpleDataSource().apply {
                                    setURL("jdbc:postgresql://127.0.0.1:1/pulverfass_test")
                                    user = "postgres"
                                    password = "postgres"
                                },
                        ),
                )
            brokenGateway.onLobbyEventAccepted(
                event = LobbyCreated(lobbyCode),
                previousState = currentState,
                currentState = currentState,
            )

            val readiness = brokenGateway.readiness()
            assertEquals("DOWN", readiness.state.name)
            assertNotNull(readiness.detail)
        }

    @Test
    fun `disabled callbacks are no op and close action is executed`() =
        runBlocking {
            val disabled = LobbyPersistenceCallbacks.disabled()
            val currentState = runningState(LobbyCode("PG13"), stateVersion = 1L)
            val snapshot = snapshotBuilder.buildSnapshotBroadcast(currentState)

            disabled.onLobbyEventAccepted(
                event = LobbyCreated(currentState.lobbyCode),
                previousState = currentState,
                currentState = currentState,
            )
            disabled.onPhaseBoundaryBroadcast(
                at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent(
                    lobbyCode = currentState.lobbyCode,
                    previousPhase = TurnPhase.REINFORCEMENTS,
                    nextPhase = TurnPhase.ATTACK,
                    activePlayerId = PlayerId(1),
                    turnCount = 1,
                    stateVersion = 1L,
                ),
            )
            disabled.onSnapshotBroadcast(snapshot)
            disabled.onSnapshotBroadcast(currentState, snapshot)
            assertEquals("DISABLED", disabled.readiness().state.name)

            val closed = AtomicBoolean(false)
            DatabaseBackedLobbyPersistenceGateway(
                store = store,
                closeAction = { closed.set(true) },
            ).close()
            assertTrue(closed.get())
        }

    private fun runningState(
        lobbyCode: LobbyCode,
        stateVersion: Long,
    ): GameState {
        val hostId = PlayerId(1)
        val guestId = PlayerId(2)
        val thirdPlayerId = PlayerId(3)
        return GameState.initial(
            lobbyCode = lobbyCode,
            mapDefinition = mapDefinition,
            players = listOf(hostId, guestId, thirdPlayerId),
            playerDisplayNames =
                mapOf(
                    hostId to "Host",
                    guestId to "Guest",
                    thirdPlayerId to "Third",
                ),
        ).copy(
            lobbyOwner = hostId,
            status = GameStatus.RUNNING,
            gameStarted = true,
            stateVersion = stateVersion,
            processedEventCount = stateVersion,
            turnNumber = 1,
            gameRandomSeed = 77L,
            gameRandomState = 88L,
            turnState =
                TurnState(
                    activePlayerId = hostId,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    turnCount = 1,
                    startPlayerId = hostId,
                ),
        )
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

    private data class TestDatabaseConfig(
        val jdbcUrl: String,
        val username: String,
        val password: String,
    )
}
