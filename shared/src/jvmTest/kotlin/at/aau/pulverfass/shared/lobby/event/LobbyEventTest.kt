package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Smoke-Test für die wichtigsten LobbyEvent-Datenklassen.
 *
 * Der Test prüft keine Spiellogik, sondern stellt sicher, dass die Events mit
 * realistischen Beispielwerten gebaut werden können. Das ist besonders für neue
 * Events wie CheatReinforcementBonusUsedEvent nützlich, weil sie später im
 * Reducer und in der Persistenz wiederverwendet werden.
 */
class LobbyEventTest {
    @Test
    fun `should instantiate sample lobby events consistently`() {
        val lobbyCode = LobbyCode("1003")
        val playerId = PlayerId(7)

        val events =
            listOf<LobbyEvent>(
                FortifyMoveAppliedEvent(
                    lobbyCode,
                    playerId,
                    TerritoryId("alpha"),
                    TerritoryId("beta"),
                    2,
                ),
                FortifyUsedSetEvent(lobbyCode, used = true),
                PlayerJoined(lobbyCode, playerId, "Alice"),
                PlayerLeft(lobbyCode, playerId, "quit"),
                StartPlayerConfigured(lobbyCode, playerId, PlayerId(1)),
                TurnEnded(lobbyCode, playerId),
                LobbyCreated(lobbyCode),
                LobbyClosed(lobbyCode, "finished"),
                MatchEndedEvent(lobbyCode, MatchEndReason.DECK_EMPTY),
                CardSetTradedInEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerId,
                    cardIds = listOf(CardId("card-1"), CardId("card-2"), CardId("card-3")),
                    value = 2,
                    tradeIndex = 1,
                ),
                CardDrawnEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerId,
                    cardId = CardId("drawn-card"),
                ),
                CheatReinforcementBonusUsedEvent(lobbyCode, playerId),
                AttackResolvedEvent(
                    lobbyCode = lobbyCode,
                    attackerPlayerId = playerId,
                    defenderPlayerId = PlayerId(8),
                    fromTerritoryId = TerritoryId("alpha"),
                    toTerritoryId = TerritoryId("beta"),
                    attackTroops = 3,
                    sourceTroopsBefore = 5,
                    targetTroopsBefore = 2,
                    requestedAttackDice = 3,
                    attackDice = 3,
                    defendDice = 2,
                    attackerRolls = listOf(6, 5, 4),
                    defenderRolls = listOf(2, 1),
                    rngTrace = listOf(6, 4, 5, 1, 2),
                    rngStateBefore = 1,
                    rngStateAfter = 2,
                    attackerLosses = 0,
                    defenderLosses = 2,
                    attackerRemaining = 5,
                    defenderRemaining = 0,
                    occupyingTroopCount = 3,
                    minOccupyingTroops = 3,
                ),
                PlayerEliminatedEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(8),
                    eliminatedByPlayerId = playerId,
                ),
                MatchEndedEvent(
                    lobbyCode = lobbyCode,
                    reason = MatchEndReason.TERRITORY_DOMINATION,
                    winnerPlayerId = playerId,
                ),
                PendingReinforcementsSetEvent(lobbyCode, playerId, 5),
                PendingReinforcementsChangedEvent(lobbyCode, playerId, 2),
                PlayerCardsRemovedEvent(
                    lobbyCode,
                    playerId,
                    listOf(CardId("card-7"), CardId("card-8")),
                ),
                SystemTick(lobbyCode, tick = 5),
                TurnStateUpdatedEvent(
                    lobbyCode = lobbyCode,
                    activePlayerId = playerId,
                    turnPhase = TurnPhase.ATTACK,
                    turnCount = 2,
                    startPlayerId = playerId,
                ),
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), playerId),
                TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("alpha"), 3),
                TimeoutTriggered(lobbyCode, target = "turn", timeoutMillis = 30_000),
                InvalidActionDetected(lobbyCode, playerId, "move rejected"),
            )

        assertEquals(24, events.size)

        assertEquals(lobbyCode, events.first().lobbyCode)
        assertEquals("finished", (events[7] as LobbyClosed).reason)
    }

    @Test
    fun `should support exhaustive when over event hierarchies`() {
        val lobbyCode = LobbyCode("1071")

        val rootResult =
            when (val event: LobbyEvent = LobbyCreated(lobbyCode)) {
                is ExternalLobbyEvent -> "external:${event.lobbyCode.value}"
                is InternalLobbyEvent -> "internal:${event.lobbyCode.value}"
            }

        val externalResult =
            when (
                val event: ExternalLobbyEvent =
                    FortifyMoveAppliedEvent(
                        lobbyCode,
                        PlayerId(3),
                        TerritoryId("alpha"),
                        TerritoryId("beta"),
                        1,
                    )
            ) {
                is FortifyMoveAppliedEvent -> "fortify:${event.playerId.value}"
                is GameStarted -> "gameStarted:${event.lobbyCode.value}"
                is PlayerJoined -> "joined:${event.playerId.value}"
                is PlayerKicked -> "kicked:${event.targetPlayerId.value}"
                is PlayerLeft -> "left:${event.playerId.value}"
                is StartPlayerConfigured -> "startPlayer:${event.startPlayerId.value}"
                is TurnEnded -> "turnEnded:${event.playerId.value}"
            }

        val internalResult =
            when (val event: InternalLobbyEvent = LobbyClosed(lobbyCode, "done")) {
                is AttackResolvedEvent -> event.rngTrace.size.toString()
                is CardDrawnEvent -> event.cardId.value
                is CardSetTradedInEvent -> event.value.toString()
                is CheatReinforcementBonusUsedEvent -> event.playerId.value.toString()
                is FortifyUsedSetEvent -> event.used.toString()
                is InvalidActionDetected -> event.reason
                is LobbyClosed -> event.reason.orEmpty()
                is LobbyCreated -> "created"
                is MatchEndedEvent -> event.reason.name
                is PendingReinforcementsChangedEvent -> event.delta.toString()
                is PendingReinforcementsSetEvent -> event.amount.toString()
                is PlayerEliminatedEvent -> event.eliminatedByPlayerId.value.toString()
                is PlayerCardsRemovedEvent -> event.cardIds.size.toString()
                is SystemTick -> event.tick.toString()
                is TerritoryOwnerChangedEvent -> event.territoryId.value
                is TerritoryTroopsChangedEvent -> event.troopCount.toString()
                is TimeoutTriggered -> event.target
                is TurnStateUpdatedEvent -> event.turnPhase.name
            }

        assertEquals("internal:1071", rootResult)
        assertEquals("fortify:3", externalResult)
        assertEquals("done", internalResult)
    }

    @Test
    fun `should forward lobby code through relevant events`() {
        val lobbyCode = LobbyCode("1440")
        val playerId = PlayerId(4)

        val events =
            listOf<LobbyEvent>(
                FortifyMoveAppliedEvent(
                    lobbyCode,
                    playerId,
                    TerritoryId("alpha"),
                    TerritoryId("beta"),
                    1,
                ),
                FortifyUsedSetEvent(lobbyCode, used = true),
                PlayerJoined(lobbyCode, playerId, "Bob"),
                PlayerLeft(lobbyCode, playerId),
                StartPlayerConfigured(lobbyCode, playerId, PlayerId(1)),
                TurnEnded(lobbyCode, playerId),
                LobbyCreated(lobbyCode),
                LobbyClosed(lobbyCode),
                MatchEndedEvent(lobbyCode, MatchEndReason.DECK_EMPTY),
                CardSetTradedInEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerId,
                    cardIds = listOf(CardId("card-a"), CardId("card-b"), CardId("card-c")),
                    value = 2,
                    tradeIndex = 1,
                ),
                CardDrawnEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerId,
                    cardId = CardId("drawn-card"),
                ),
                CheatReinforcementBonusUsedEvent(lobbyCode, playerId),
                AttackResolvedEvent(
                    lobbyCode = lobbyCode,
                    attackerPlayerId = playerId,
                    defenderPlayerId = PlayerId(5),
                    fromTerritoryId = TerritoryId("alpha"),
                    toTerritoryId = TerritoryId("beta"),
                    attackTroops = 3,
                    sourceTroopsBefore = 5,
                    targetTroopsBefore = 2,
                    requestedAttackDice = 3,
                    attackDice = 3,
                    defendDice = 2,
                    attackerRolls = listOf(6, 5, 4),
                    defenderRolls = listOf(2, 1),
                    rngTrace = listOf(6, 4, 5, 1, 2),
                    rngStateBefore = 1,
                    rngStateAfter = 2,
                    attackerLosses = 0,
                    defenderLosses = 2,
                    attackerRemaining = 5,
                    defenderRemaining = 0,
                    occupyingTroopCount = 3,
                    minOccupyingTroops = 3,
                ),
                PlayerEliminatedEvent(
                    lobbyCode = lobbyCode,
                    playerId = PlayerId(5),
                    eliminatedByPlayerId = playerId,
                ),
                PendingReinforcementsSetEvent(lobbyCode, playerId, 4),
                PendingReinforcementsChangedEvent(lobbyCode, playerId, -1),
                PlayerCardsRemovedEvent(
                    lobbyCode,
                    playerId,
                    listOf(CardId("card-x"), CardId("card-y")),
                ),
                SystemTick(lobbyCode, 0),
                TurnStateUpdatedEvent(
                    lobbyCode = lobbyCode,
                    activePlayerId = playerId,
                    turnPhase = TurnPhase.REINFORCEMENTS,
                    turnCount = 1,
                    startPlayerId = playerId,
                ),
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), playerId),
                TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("alpha"), 2),
                TimeoutTriggered(lobbyCode, "heartbeat", 1_000),
                InvalidActionDetected(lobbyCode, reason = "invalid"),
            )

        events.forEach { event -> assertEquals(lobbyCode, event.lobbyCode) }
    }

    @Test
    fun `should expose player left reason consistently`() {
        val leftWithReason = PlayerLeft(LobbyCode("1291"), PlayerId(9), "quit")
        val leftWithoutReason = PlayerLeft(LobbyCode("1291"), PlayerId(9))

        assertEquals("quit", leftWithReason.reason)
        assertEquals(null, leftWithoutReason.reason)
    }

    @Test
    fun `should expose technical event properties consistently`() {
        val fortifyApplied =
            FortifyMoveAppliedEvent(
                LobbyCode("1000"),
                PlayerId(3),
                TerritoryId("alpha"),
                TerritoryId("beta"),
                2,
            )
        val fortifyUsed = FortifyUsedSetEvent(LobbyCode("1002"), used = true)
        val invalidAction = InvalidActionDetected(LobbyCode("1001"), PlayerId(2), "invalid")
        val tick = SystemTick(LobbyCode("1048"), 4)
        val timeout = TimeoutTriggered(LobbyCode("1058"), "turn", 3_000)

        assertEquals(PlayerId(3), fortifyApplied.playerId)
        assertEquals(TerritoryId("alpha"), fortifyApplied.fromTerritoryId)
        assertEquals(true, fortifyUsed.used)
        assertEquals(PlayerId(2), invalidAction.playerId)
        assertEquals("invalid", invalidAction.reason)
        assertEquals(4, tick.tick)
        assertEquals("turn", timeout.target)
        assertEquals(3_000, timeout.timeoutMillis)
    }

    @Test
    fun `should validate technical event arguments`() {
        assertThrows(IllegalArgumentException::class.java) {
            CardSetTradedInEvent(
                lobbyCode = LobbyCode("1060"),
                playerId = PlayerId(1),
                cardIds = listOf(CardId("card-a"), CardId("card-b")),
                value = 2,
                tradeIndex = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CardSetTradedInEvent(
                lobbyCode = LobbyCode("1065"),
                playerId = PlayerId(1),
                cardIds = listOf(CardId("card-a"), CardId("card-a"), CardId("card-b")),
                value = 2,
                tradeIndex = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerCardsRemovedEvent(
                lobbyCode = LobbyCode("1066"),
                playerId = PlayerId(1),
                cardIds = emptyList(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerCardsRemovedEvent(
                lobbyCode = LobbyCode("1067"),
                playerId = PlayerId(1),
                cardIds = listOf(CardId("card-a"), CardId("card-a")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FortifyMoveAppliedEvent(
                LobbyCode("1139"),
                PlayerId(1),
                TerritoryId("alpha"),
                TerritoryId("alpha"),
                1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FortifyMoveAppliedEvent(
                LobbyCode("1140"),
                PlayerId(1),
                TerritoryId("alpha"),
                TerritoryId("beta"),
                0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            InvalidActionDetected(LobbyCode("1116"), reason = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SystemTick(LobbyCode("1131"), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeoutTriggered(LobbyCode("1141"), "", 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeoutTriggered(LobbyCode("1176"), "turn", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnStateUpdatedEvent(
                lobbyCode = LobbyCode("1191"),
                activePlayerId = PlayerId(1),
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 0,
                startPlayerId = PlayerId(1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnStateUpdatedEvent(
                lobbyCode = LobbyCode("1197"),
                activePlayerId = PlayerId(1),
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 1,
                startPlayerId = PlayerId(1),
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
            )
        }
    }
}
