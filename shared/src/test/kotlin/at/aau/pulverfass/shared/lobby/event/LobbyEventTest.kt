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

class LobbyEventTest {
    @Test
    fun `should instantiate sample lobby events consistently`() {
        val lobbyCode = LobbyCode("AB12")
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
                CardSetTradedInEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerId,
                    cardIds = listOf(CardId("card-1"), CardId("card-2"), CardId("card-3")),
                    value = 2,
                    tradeIndex = 1,
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

        assertEquals(21, events.size)
        assertEquals(lobbyCode, events.first().lobbyCode)
        assertEquals("finished", (events[7] as LobbyClosed).reason)
    }

    @Test
    fun `should support exhaustive when over event hierarchies`() {
        val lobbyCode = LobbyCode("CD34")

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
                is CardSetTradedInEvent -> event.value.toString()
                is CheatReinforcementBonusUsedEvent -> event.playerId.value.toString()
                is FortifyUsedSetEvent -> event.used.toString()
                is InvalidActionDetected -> event.reason
                is LobbyClosed -> event.reason.orEmpty()
                is LobbyCreated -> "created"
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

        assertEquals("internal:CD34", rootResult)
        assertEquals("fortify:3", externalResult)
        assertEquals("done", internalResult)
    }

    @Test
    fun `should forward lobby code through relevant events`() {
        val lobbyCode = LobbyCode("ZX90")
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
                CardSetTradedInEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerId,
                    cardIds = listOf(CardId("card-a"), CardId("card-b"), CardId("card-c")),
                    value = 2,
                    tradeIndex = 1,
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
        val leftWithReason = PlayerLeft(LobbyCode("PL12"), PlayerId(9), "quit")
        val leftWithoutReason = PlayerLeft(LobbyCode("PL12"), PlayerId(9))

        assertEquals("quit", leftWithReason.reason)
        assertEquals(null, leftWithoutReason.reason)
    }

    @Test
    fun `should expose technical event properties consistently`() {
        val fortifyApplied =
            FortifyMoveAppliedEvent(
                LobbyCode("AA10"),
                PlayerId(3),
                TerritoryId("alpha"),
                TerritoryId("beta"),
                2,
            )
        val fortifyUsed = FortifyUsedSetEvent(LobbyCode("AA12"), used = true)
        val invalidAction = InvalidActionDetected(LobbyCode("AA11"), PlayerId(2), "invalid")
        val tick = SystemTick(LobbyCode("BB22"), 4)
        val timeout = TimeoutTriggered(LobbyCode("CC33"), "turn", 3_000)

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
                lobbyCode = LobbyCode("CC44"),
                playerId = PlayerId(1),
                cardIds = listOf(CardId("card-a"), CardId("card-b")),
                value = 2,
                tradeIndex = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CardSetTradedInEvent(
                lobbyCode = LobbyCode("CC55"),
                playerId = PlayerId(1),
                cardIds = listOf(CardId("card-a"), CardId("card-a"), CardId("card-b")),
                value = 2,
                tradeIndex = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerCardsRemovedEvent(
                lobbyCode = LobbyCode("CC56"),
                playerId = PlayerId(1),
                cardIds = emptyList(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerCardsRemovedEvent(
                lobbyCode = LobbyCode("CC57"),
                playerId = PlayerId(1),
                cardIds = listOf(CardId("card-a"), CardId("card-a")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FortifyMoveAppliedEvent(
                LobbyCode("FC10"),
                PlayerId(1),
                TerritoryId("alpha"),
                TerritoryId("alpha"),
                1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FortifyMoveAppliedEvent(
                LobbyCode("FC12"),
                PlayerId(1),
                TerritoryId("alpha"),
                TerritoryId("beta"),
                0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            InvalidActionDetected(LobbyCode("DD44"), reason = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SystemTick(LobbyCode("EE55"), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeoutTriggered(LobbyCode("FF66"), "", 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeoutTriggered(LobbyCode("GG77"), "turn", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnStateUpdatedEvent(
                lobbyCode = LobbyCode("HH88"),
                activePlayerId = PlayerId(1),
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 0,
                startPlayerId = PlayerId(1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnStateUpdatedEvent(
                lobbyCode = LobbyCode("II99"),
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
