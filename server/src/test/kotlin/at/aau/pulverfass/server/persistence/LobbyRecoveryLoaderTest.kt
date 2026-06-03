package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.server.map.ClasspathMapDefinitionRepository
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.CheatReinforcementBonusUsedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
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
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.DeckState
import at.aau.pulverfass.shared.lobby.state.DiscardPileState
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.HandState
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class LobbyRecoveryLoaderTest {
    private val lobbyCode = LobbyCode("LR11")
    private val mapDefinition =
        ClasspathMapDefinitionRepository.loadDefault().defaultMapDefinition()

    @Test
    fun `persisted snapshot round-trips complete game state`() {
        val hostId = PlayerId(1)
        val guestId = PlayerId(2)
        val state =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = mapDefinition,
                players = listOf(hostId, guestId),
                playerDisplayNames = mapOf(hostId to "Alice", guestId to "Bob"),
            ).copy(
                lobbyOwner = hostId,
                status = GameStatus.RUNNING,
                gameStarted = true,
                gameRandomSeed = 99L,
                gameRandomState = 1234L,
                fortifyUsedThisTurn = true,
                stateVersion = 7,
                processedEventCount = 7,
                tradedInSetCount = 2,
                lastInvalidActionReason = "none",
                handState =
                    HandState(
                        cardsByPlayer =
                            mapOf(
                                hostId to
                                    listOf(
                                        CardState(
                                            cardId = CardId("card-a"),
                                            type = CardType.A,
                                        ),
                                        CardState(
                                            cardId = CardId("card-joker"),
                                            type = CardType.JOKER,
                                        ),
                                    ),
                            ),
                    ),
                deckState =
                    DeckState(
                        cards =
                            listOf(
                                CardState(
                                    cardId = CardId("deck-1"),
                                    type = CardType.B,
                                ),
                            ),
                    ),
                discardPileState =
                    DiscardPileState(
                        cards =
                            listOf(
                                CardState(
                                    cardId = CardId("discard-1"),
                                    type = CardType.C,
                                ),
                            ),
                    ),
            )

        val restored = PersistedLobbyRecoverySnapshot.fromGameState(state).toGameState()

        assertEquals(state.copy(lastEventContext = null), restored)
    }

    @Test
    fun `persisted snapshot rejects mismatched determinism metadata`() {
        val snapshot =
            PersistedLobbyRecoverySnapshot.fromGameState(
                GameState.initial(
                    lobbyCode = lobbyCode,
                    mapDefinition = mapDefinition,
                ),
            ).copy(
                determinism =
                    PublicDeterminismMetadataSnapshot(
                        mapHash = "wrong-hash",
                        schemaVersion = mapDefinition.schemaVersion,
                        seed = 42L,
                    ),
            )

        assertThrows(IllegalStateException::class.java) {
            snapshot.toGameState()
        }
    }

    @Test
    fun `persisted snapshot restores rng fields from fallback determinism metadata`() {
        val state =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = mapDefinition,
                players = listOf(PlayerId(1)),
                playerDisplayNames = mapOf(PlayerId(1) to "Alice"),
            ).copy(
                status = GameStatus.RUNNING,
                gameStarted = true,
                gameRandomSeed = 77L,
                gameRandomState = 99L,
            )

        val restored =
            PersistedLobbyRecoverySnapshot
                .fromGameState(state)
                .copy(
                    gameRandomSeed = null,
                    gameRandomState = null,
                ).toGameState()

        assertEquals(77L, restored.gameRandomSeed)
        assertEquals(77L, restored.gameRandomState)
    }

    @Test
    fun `persisted snapshot falls back to explicit random seed when state is missing`() {
        val state =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = mapDefinition,
                players = listOf(PlayerId(1)),
                playerDisplayNames = mapOf(PlayerId(1) to "Alice"),
            ).copy(
                status = GameStatus.RUNNING,
                gameStarted = true,
                gameRandomSeed = 88L,
                gameRandomState = 144L,
            )

        val restored =
            PersistedLobbyRecoverySnapshot
                .fromGameState(state)
                .copy(gameRandomState = null)
                .toGameState()

        assertEquals(88L, restored.gameRandomSeed)
        assertEquals(88L, restored.gameRandomState)
    }

    @Test
    fun `persisted snapshot rejects missing map definition on serialization`() {
        val state =
            GameState.initial(lobbyCode = lobbyCode).copy(
                mapDefinition = null,
            )

        assertThrows(IllegalStateException::class.java) {
            PersistedLobbyRecoverySnapshot.fromGameState(state)
        }
    }

    @Test
    fun `persisted turn state snapshot round trips paused and unpaused variants`() {
        val paused =
            PersistedTurnStateSnapshot.fromTurnState(
                at.aau.pulverfass.shared.lobby.state.TurnState(
                    activePlayerId = PlayerId(3),
                    turnPhase = TurnPhase.ATTACK,
                    turnCount = 5,
                    startPlayerId = PlayerId(1),
                    isPaused = true,
                    pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                    pausedPlayerId = PlayerId(3),
                ),
            )
        val unpaused =
            PersistedTurnStateSnapshot.fromTurnState(
                at.aau.pulverfass.shared.lobby.state.TurnState(
                    activePlayerId = PlayerId(4),
                    turnPhase = TurnPhase.FORTIFY,
                    turnCount = 6,
                    startPlayerId = PlayerId(2),
                    isPaused = false,
                ),
            )

        assertEquals(TurnPauseReasons.WAITING_FOR_PLAYER, paused.toTurnState().pauseReason)
        assertEquals(PlayerId(3), paused.toTurnState().pausedPlayerId)
        assertEquals(null, unpaused.toTurnState().pauseReason)
        assertEquals(null, unpaused.toTurnState().pausedPlayerId)
    }

    @Test
    fun `toLobbyEvent maps all supported persisted event types`() {
        val createdAt = Instant.parse("2026-01-01T00:00:00Z")

        assertEquals(
            AttackResolvedEvent(
                lobbyCode = lobbyCode,
                attackerPlayerId = PlayerId(1),
                defenderPlayerId = PlayerId(2),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("beta"),
                attackTroops = 3,
                sourceTroopsBefore = 5,
                targetTroopsBefore = 2,
                requestedAttackDice = 3,
                attackDice = 3,
                defendDice = 2,
                attackerRolls = listOf(5, 4, 3),
                defenderRolls = listOf(2, 1),
                rngTrace = listOf(5, 3, 4, 1, 2),
                rngStateBefore = 2L,
                rngStateAfter = 3L,
                attackerLosses = 0,
                defenderLosses = 2,
                attackerRemaining = 5,
                defenderRemaining = 0,
                occupyingTroopCount = 3,
                minOccupyingTroops = 3,
            ),
            record(
                "attack_resolved",
                """
                {"lobbyCode":"LR11","attackerPlayerId":1,"defenderPlayerId":2,
                "fromTerritoryId":"alpha","toTerritoryId":"beta","attackTroops":3,"sourceTroopsBefore":5,
                "targetTroopsBefore":2,"requestedAttackDice":3,"attackDice":3,"defendDice":2,
                "attackerRolls":[5,4,3],"defenderRolls":[2,1],"rngTrace":[5,3,4,1,2],
                "rngStateBefore":2,"rngStateAfter":3,"attackerLosses":0,"defenderLosses":2,
                "attackerRemaining":5,"defenderRemaining":0,"occupyingTroopCount":3,
                "minOccupyingTroops":3}
                """.trimIndent().replace("\n", ""),
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            PlayerEliminatedEvent(
                lobbyCode = lobbyCode,
                playerId = PlayerId(2),
                eliminatedByPlayerId = PlayerId(1),
                stateVersion = 2L,
            ),
            record(
                "player_eliminated",
                """{"lobbyCode":"LR11","playerId":2,"eliminatedByPlayerId":1,"stateVersion":2}""",
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            CardSetTradedInEvent(
                lobbyCode = lobbyCode,
                playerId = PlayerId(5),
                cardIds = listOf(CardId("card-a"), CardId("card-b"), CardId("card-c")),
                value = 6,
                tradeIndex = 3,
            ),
            record(
                "card_set_traded_in",
                """
                {"lobbyCode":"LR11","playerId":5,"cardIds":["card-a","card-b","card-c"],"value":6,"tradeIndex":3}
                """.trimIndent(),
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            LobbyCreated(lobbyCode),
            record(
                eventType = "lobby_created",
                eventJson = """{"lobbyCode":"LR11"}""",
                createdAt = createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            LobbyClosed(lobbyCode, "done"),
            record(
                eventType = "lobby_closed",
                eventJson = """{"lobbyCode":"LR11","reason":"done"}""",
                createdAt = createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            PlayerJoined(lobbyCode, PlayerId(1), "Alice"),
            record(
                "player_joined",
                """{"lobbyCode":"LR11","playerId":1,"playerDisplayName":"Alice"}""",
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            PlayerLeft(lobbyCode, PlayerId(2), "quit"),
            record(
                "player_left",
                """{"lobbyCode":"LR11","playerId":2,"reason":"quit"}""",
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            PlayerKicked(lobbyCode, PlayerId(2), PlayerId(1)),
            record(
                "player_kicked",
                """{"lobbyCode":"LR11","targetPlayerId":2,"requesterPlayerId":1}""",
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            StartPlayerConfigured(lobbyCode, PlayerId(2), PlayerId(1)),
            record(
                "start_player_configured",
                """{"lobbyCode":"LR11","startPlayerId":2,"requesterPlayerId":1}""",
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            GameStarted(lobbyCode, 123L),
            record(
                eventType = "game_started",
                eventJson = """{"lobbyCode":"LR11","randomSeed":123}""",
                createdAt = createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            InvalidActionDetected(lobbyCode, PlayerId(3), "invalid"),
            record(
                "invalid_action_detected",
                """{"lobbyCode":"LR11","playerId":3,"reason":"invalid"}""",
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            SystemTick(lobbyCode, 77L),
            record(
                eventType = "system_tick",
                eventJson = """{"lobbyCode":"LR11","tick":77}""",
                createdAt = createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            TerritoryOwnerChangedEvent(
                lobbyCode = lobbyCode,
                territoryId = TerritoryId("territory-1"),
                ownerId = PlayerId(4),
                stateVersion = 5L,
            ),
            record(
                "territory_owner_changed",
                """{"lobbyCode":"LR11","territoryId":"territory-1","ownerId":4,"stateVersion":5}""",
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            TerritoryTroopsChangedEvent(
                lobbyCode = lobbyCode,
                territoryId = TerritoryId("territory-2"),
                troopCount = 9,
                stateVersion = 6L,
            ),
            record(
                "territory_troops_changed",
                """
                {"lobbyCode":"LR11","territoryId":"territory-2","troopCount":9,"stateVersion":6}
                """.trimIndent(),
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            FortifyMoveAppliedEvent(
                lobbyCode = lobbyCode,
                playerId = PlayerId(7),
                fromTerritoryId = TerritoryId("territory-3"),
                toTerritoryId = TerritoryId("territory-4"),
                troopCount = 3,
            ),
            record(
                "fortify_move_applied",
                """
                {"lobbyCode":"LR11","playerId":7,"fromTerritoryId":"territory-3",
                "toTerritoryId":"territory-4","troopCount":3}
                """.trimIndent().replace("\n", ""),
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            FortifyUsedSetEvent(
                lobbyCode = lobbyCode,
                used = true,
            ),
            record(
                "fortify_used_set",
                """{"lobbyCode":"LR11","used":true}""",
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            TimeoutTriggered(lobbyCode, "setup", 3000L),
            record(
                "timeout_triggered",
                """{"lobbyCode":"LR11","target":"setup","timeoutMillis":3000}""",
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            TurnEnded(lobbyCode, PlayerId(5)),
            record(
                eventType = "turn_ended",
                eventJson = """{"lobbyCode":"LR11","playerId":5}""",
                createdAt = createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            TurnStateUpdatedEvent(
                lobbyCode = lobbyCode,
                activePlayerId = PlayerId(6),
                turnPhase = TurnPhase.REINFORCEMENTS,
                turnCount = 4,
                startPlayerId = PlayerId(1),
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                pausedPlayerId = PlayerId(6),
            ),
            record(
                "turn_state_updated",
                """
                {"lobbyCode":"LR11","activePlayerId":6,"turnPhase":"REINFORCEMENTS","turnCount":4,
                "startPlayerId":1,"isPaused":true,"pauseReason":"WAITING_FOR_PLAYER","pausedPlayerId":6}
                """.trimIndent().replace("\n", ""),
                createdAt,
            ).toLobbyEvent(),
        )
        assertEquals(
            CheatReinforcementBonusUsedEvent(
                lobbyCode = lobbyCode,
                playerId = PlayerId(5),
            ),
            record(
                "cheat_reinforcement_bonus_used",
                """{"lobbyCode":"LR11","playerId":5}""",
                createdAt,
            ).toLobbyEvent(),
        )
    }

    @Test
    fun `toLobbyEvent rejects mismatched lobby code and unknown types`() {
        assertThrows(IllegalStateException::class.java) {
            record("lobby_created", """{"lobbyCode":"AB12"}""").toLobbyEvent()
        }
        assertThrows(IllegalStateException::class.java) {
            record("unknown_event", """{"lobbyCode":"LR11"}""").toLobbyEvent()
        }
    }

    @Test
    fun `toLobbyEvent maps nullable optional fields`() {
        assertEquals(
            LobbyClosed(lobbyCode, null),
            record(
                eventType = "lobby_closed",
                eventJson = """{"lobbyCode":"LR11","reason":null}""",
            ).toLobbyEvent(),
        )
        assertEquals(
            PlayerLeft(lobbyCode, PlayerId(2), null),
            record(
                eventType = "player_left",
                eventJson = """{"lobbyCode":"LR11","playerId":2,"reason":null}""",
            ).toLobbyEvent(),
        )
        assertEquals(
            InvalidActionDetected(lobbyCode, null, "invalid"),
            record(
                eventType = "invalid_action_detected",
                eventJson = """{"lobbyCode":"LR11","playerId":null,"reason":"invalid"}""",
            ).toLobbyEvent(),
        )
        assertEquals(
            TerritoryOwnerChangedEvent(
                lobbyCode = lobbyCode,
                territoryId = TerritoryId("territory-1"),
                ownerId = null,
                stateVersion = null,
            ),
            record(
                eventType = "territory_owner_changed",
                eventJson =
                    """
                    {"lobbyCode":"LR11","territoryId":"territory-1","ownerId":null,"stateVersion":null}
                    """.trimIndent(),
            ).toLobbyEvent(),
        )
        assertEquals(
            TurnStateUpdatedEvent(
                lobbyCode = lobbyCode,
                activePlayerId = PlayerId(6),
                turnPhase = TurnPhase.ATTACK,
                turnCount = 4,
                startPlayerId = PlayerId(1),
                isPaused = false,
                pauseReason = null,
                pausedPlayerId = null,
            ),
            record(
                eventType = "turn_state_updated",
                eventJson =
                    """
                    {"lobbyCode":"LR11","activePlayerId":6,"turnPhase":"ATTACK","turnCount":4,
                    "startPlayerId":1,"isPaused":false,"pauseReason":null,"pausedPlayerId":null}
                    """.trimIndent().replace("\n", ""),
            ).toLobbyEvent(),
        )
    }

    @Test
    fun `toLobbyEvent rejects malformed persisted payload fields`() {
        val malformedPayloads =
            listOf(
                record(
                    eventType = "attack_resolved",
                    eventJson =
                        """
                        {"lobbyCode":"LR11","attackerPlayerId":1,"defenderPlayerId":2,
                        "fromTerritoryId":"alpha","toTerritoryId":"beta","attackTroops":"x",
                        "sourceTroopsBefore":5,"targetTroopsBefore":2,"requestedAttackDice":3,
                        "attackDice":3,"defendDice":2,"attackerRolls":[5,4,3],"defenderRolls":[2,1],
                        "rngTrace":[5,3,4,1,2],"rngStateBefore":2,"rngStateAfter":3,
                        "attackerLosses":0,"defenderLosses":2,"attackerRemaining":5,
                        "defenderRemaining":0,"occupyingTroopCount":3,"minOccupyingTroops":3}
                        """.trimIndent().replace("\n", ""),
                ),
                record(
                    eventType = "turn_state_updated",
                    eventJson =
                        """
                        {"lobbyCode":"LR11","activePlayerId":6,"turnPhase":"ATTACK",
                        "turnCount":4,"startPlayerId":1,"isPaused":"nope"}
                        """.trimIndent().replace("\n", ""),
                ),
                record(
                    eventType = "card_set_traded_in",
                    eventJson =
                        """
                        {"lobbyCode":"LR11","playerId":5,"cardIds":["card-a",3],"value":6,"tradeIndex":3}
                        """.trimIndent(),
                ),
            )

        malformedPayloads.forEach { payload ->
            val exception =
                assertThrows(RuntimeException::class.java) {
                    payload.toLobbyEvent()
                }
            assertTrue(exception.message!!.contains("muss"))
        }
    }

    private fun record(
        eventType: String,
        eventJson: String,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ) = PersistedLobbyEventRecord(
        id = 1L,
        lobbyCode = lobbyCode,
        stateVersion = 1L,
        turnCount = 0,
        eventType = eventType,
        eventJson = eventJson,
        createdAt = createdAt,
    )
}
