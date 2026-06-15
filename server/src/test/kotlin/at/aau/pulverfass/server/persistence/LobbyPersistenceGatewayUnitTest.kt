package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.server.routing.PublicGameStateBuilder
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardDrawnEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.CheatReinforcementBonusUsedEvent
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.MatchEndReason
import at.aau.pulverfass.shared.lobby.event.MatchEndedEvent
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
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger
import javax.sql.DataSource

class LobbyPersistenceGatewayUnitTest {
    private val mapDefinition = MapConfigLoader.loadDefault()
    private val snapshotBuilder = PublicGameStateBuilder()

    @Test
    fun `toPersistedPayload maps supported events to stable type ids and payload fields`() {
        val lobbyCode = LobbyCode("UT11")
        val hostId = PlayerId(1)
        val guestId = PlayerId(2)
        val thirdPlayerId = PlayerId(3)

        val mappings =
            listOf(
                LobbyCreated(lobbyCode) to "lobby_created",
                attackResolvedEvent(lobbyCode, hostId, guestId) to "attack_resolved",
                PlayerEliminatedEvent(lobbyCode, guestId, hostId, stateVersion = 2L) to
                    "player_eliminated",
                CardSetTradedInEvent(
                    lobbyCode = lobbyCode,
                    playerId = hostId,
                    cardIds = listOf(CardId("ca"), CardId("cb"), CardId("cc")),
                    value = 8,
                    tradeIndex = 2,
                ) to "card_set_traded_in",
                CardDrawnEvent(lobbyCode, hostId, CardId("drawn-card")) to "card_drawn",
                PendingReinforcementsSetEvent(lobbyCode, hostId, amount = 7) to
                    "pending_reinforcements_set",
                PendingReinforcementsChangedEvent(lobbyCode, hostId, delta = -2) to
                    "pending_reinforcements_changed",
                CheatReinforcementBonusUsedEvent(lobbyCode, hostId) to
                    "cheat_reinforcement_bonus_used",
                PlayerCardsRemovedEvent(
                    lobbyCode = lobbyCode,
                    playerId = hostId,
                    cardIds = listOf(CardId("ca"), CardId("cb")),
                ) to "player_cards_removed",
                LobbyClosed(lobbyCode, "done") to "lobby_closed",
                MatchEndedEvent(lobbyCode, MatchEndReason.DECK_EMPTY) to "match_ended",
                PlayerJoined(lobbyCode, thirdPlayerId, "Third") to "player_joined",
                PlayerLeft(lobbyCode, thirdPlayerId, "quit") to "player_left",
                PlayerKicked(lobbyCode, thirdPlayerId, hostId) to "player_kicked",
                StartPlayerConfigured(lobbyCode, hostId, hostId) to "start_player_configured",
                GameStarted(lobbyCode, 77L) to "game_started",
                InvalidActionDetected(lobbyCode, null, "invalid") to "invalid_action_detected",
                SystemTick(lobbyCode, 19L) to "system_tick",
                TerritoryOwnerChangedEvent(
                    lobbyCode = lobbyCode,
                    territoryId = TerritoryId("gamma"),
                    ownerId = null,
                    stateVersion = 15L,
                ) to "territory_owner_changed",
                TerritoryTroopsChangedEvent(
                    lobbyCode = lobbyCode,
                    territoryId = TerritoryId("gamma"),
                    troopCount = 9,
                    stateVersion = 16L,
                ) to "territory_troops_changed",
                TimeoutTriggered(lobbyCode, "attack", 5_000L) to "timeout_triggered",
                TurnEnded(lobbyCode, hostId) to "turn_ended",
                TurnStateUpdatedEvent(
                    lobbyCode = lobbyCode,
                    activePlayerId = guestId,
                    turnPhase = TurnPhase.FORTIFY,
                    turnCount = 2,
                    startPlayerId = hostId,
                    isPaused = true,
                    pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                    pausedPlayerId = guestId,
                ) to "turn_state_updated",
            )

        val actualTypes =
            mappings.map { (event, expectedType) ->
                val payload = persistedPayloadOf(event)
                assertEquals(expectedType, payload.type)
                payload.type
            }

        assertEquals(mappings.map { it.second }, actualTypes)

        val attackPayload =
            persistedPayloadOf(
                attackResolvedEvent(
                    lobbyCode = lobbyCode,
                    attackerPlayerId = hostId,
                    defenderPlayerId = guestId,
                ),
            ).payload
        assertEquals("alpha", attackPayload.getValue("fromTerritoryId").jsonPrimitive.content)
        assertEquals("12", attackPayload.getValue("rngStateAfter").jsonPrimitive.content)
        assertEquals("3", attackPayload.getValue("occupyingTroopCount").jsonPrimitive.content)

        val drawPayload =
            persistedPayloadOf(CardDrawnEvent(lobbyCode, hostId, CardId("drawn-card"))).payload
        assertEquals("drawn-card", drawPayload.getValue("cardId").jsonPrimitive.content)

        val matchEndedPayload =
            persistedPayloadOf(
                MatchEndedEvent(
                    lobbyCode = lobbyCode,
                    reason = MatchEndReason.TERRITORY_DOMINATION,
                    winnerPlayerId = hostId,
                ),
            ).payload
        assertEquals(
            "TERRITORY_DOMINATION",
            matchEndedPayload.getValue("reason").jsonPrimitive.content,
        )
        assertEquals("1", matchEndedPayload.getValue("winnerPlayerId").jsonPrimitive.content)

        val invalidPayload =
            persistedPayloadOf(InvalidActionDetected(lobbyCode, null, "invalid")).payload
        assertTrue(invalidPayload.getValue("playerId").toString() == "null")

        val turnPayload =
            persistedPayloadOf(
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
            ).payload
        assertEquals("FORTIFY", turnPayload.getValue("turnPhase").jsonPrimitive.content)
        assertEquals(
            "WAITING_FOR_PLAYER",
            turnPayload.getValue("pauseReason").jsonPrimitive.content,
        )
    }

    @Test
    fun `gateway reports failure details when event and snapshot persistence fail`() =
        runBlocking {
            val lobbyCode = LobbyCode("UT12")
            val state = runningState(lobbyCode, stateVersion = 4L)
            val gateway =
                DatabaseBackedLobbyPersistenceGateway(
                    store =
                        JdbcLobbyPersistenceStore(
                            dataSource = failingDataSource("boom"),
                        ),
                )

            gateway.onLobbyEventAccepted(
                event = LobbyCreated(lobbyCode),
                previousState = state,
                currentState = state,
            )
            val eventFailure = gateway.readiness()
            assertEquals("DOWN", eventFailure.state.name)
            assertNotNull(eventFailure.detail)
            assertTrue(eventFailure.detail!!.contains("Persisting lobby event"))

            gateway.onSnapshotBroadcast(
                currentState = state,
                payload = snapshotPayload(lobbyCode, state.stateVersion),
            )
            val snapshotFailure = gateway.readiness()
            assertEquals("DOWN", snapshotFailure.state.name)
            assertNotNull(snapshotFailure.detail)
            assertTrue(snapshotFailure.detail!!.contains("Persisting full snapshot"))
        }

    @Test
    fun `gateway rejects snapshot overload without state and close delegates`() {
        val lobbyCode = LobbyCode("UT13")
        val state = runningState(lobbyCode, stateVersion = 1L)
        var closed = false
        val gateway =
            DatabaseBackedLobbyPersistenceGateway(
                store = JdbcLobbyPersistenceStore(dataSource = failingDataSource("unused")),
                closeAction = { closed = true },
            )

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                gateway.onSnapshotBroadcast(snapshotPayload(lobbyCode, state.stateVersion))
            }
        }

        gateway.close()
        assertTrue(closed)
    }

    @Test
    fun `disabled callbacks stay disabled and accept all invocations`() =
        runBlocking {
            val lobbyCode = LobbyCode("UT14")
            val state = runningState(lobbyCode, stateVersion = 1L)
            val snapshot = snapshotPayload(lobbyCode, state.stateVersion)
            val callbacks = LobbyPersistenceCallbacks.disabled()

            callbacks.onLobbyEventAccepted(LobbyCreated(lobbyCode), state, state)
            callbacks.onPhaseBoundaryBroadcast(
                at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent(
                    lobbyCode = lobbyCode,
                    previousPhase = TurnPhase.REINFORCEMENTS,
                    nextPhase = TurnPhase.ATTACK,
                    activePlayerId = PlayerId(1),
                    turnCount = 1,
                    stateVersion = state.stateVersion,
                ),
            )
            callbacks.onSnapshotBroadcast(snapshot)
            callbacks.onSnapshotBroadcast(state, snapshot)

            val readiness = callbacks.readiness()
            assertEquals("DISABLED", readiness.state.name)
            assertTrue(readiness.detail!!.contains("disabled", ignoreCase = true))
        }

    private fun persistedPayloadOf(event: LobbyEvent): ReflectedPersistedPayload {
        val method =
            Class
                .forName("at.aau.pulverfass.server.persistence.LobbyPersistenceGatewayKt")
                .getDeclaredMethod("toPersistedPayload", LobbyEvent::class.java)
        method.isAccessible = true
        val reflected = method.invoke(null, event)
        val reflectedClass = reflected.javaClass
        val typeGetter = reflectedClass.getDeclaredMethod("getType")
        val payloadGetter = reflectedClass.getDeclaredMethod("getPayload")
        typeGetter.isAccessible = true
        payloadGetter.isAccessible = true
        return ReflectedPersistedPayload(
            type = typeGetter.invoke(reflected) as String,
            payload = payloadGetter.invoke(reflected) as JsonObject,
        )
    }

    private fun attackResolvedEvent(
        lobbyCode: LobbyCode,
        attackerPlayerId: PlayerId,
        defenderPlayerId: PlayerId,
    ) = AttackResolvedEvent(
        lobbyCode = lobbyCode,
        attackerPlayerId = attackerPlayerId,
        defenderPlayerId = defenderPlayerId,
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
    )

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

    private fun snapshotPayload(
        lobbyCode: LobbyCode,
        stateVersion: Long,
    ): GameStateSnapshotBroadcast =
        snapshotBuilder.buildSnapshotBroadcast(
            runningState(lobbyCode, stateVersion),
        )

    private fun failingDataSource(message: String) =
        object : DataSource {
            override fun getConnection(): Connection = throw SQLException(message)

            override fun getConnection(
                username: String?,
                password: String?,
            ): Connection = throw SQLException(message)

            override fun getLogWriter(): PrintWriter? = null

            override fun setLogWriter(out: PrintWriter?) = Unit

            override fun setLoginTimeout(seconds: Int) = Unit

            override fun getLoginTimeout(): Int = 0

            override fun getParentLogger(): Logger = throw SQLFeatureNotSupportedException()

            override fun <T : Any?> unwrap(iface: Class<T>?): T =
                throw SQLFeatureNotSupportedException()

            override fun isWrapperFor(iface: Class<*>?): Boolean = false
        }

    private data class ReflectedPersistedPayload(
        val type: String,
        val payload: JsonObject,
    )
}
