package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultMapCommandRuleServiceTest {
    private val ruleService = DefaultMapCommandRuleService()

    @Test
    fun `place troops valid creates troop update event`() {
        val playerOne = PlayerId(1)
        val baseState = sampleState()
        val state =
            baseState.copy(
                territoryStates =
                    baseState.territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(
                                    territoryId = TerritoryId("alpha"),
                                    ownerId = playerOne,
                                    troopCount = 3,
                                ),
                        ),
            )

        val events =
            ruleService.createEvents(
                state = state,
                command =
                    PlaceTroopsCommand(
                        lobbyCode = state.lobbyCode,
                        playerId = playerOne,
                        territoryId = TerritoryId("alpha"),
                        troopCount = 4,
                    ),
            )

        assertEquals(
            listOf(
                TerritoryTroopsChangedEvent(
                    lobbyCode = state.lobbyCode,
                    territoryId = TerritoryId("alpha"),
                    troopCount = 7,
                ),
            ),
            events,
        )
    }

    @Test
    fun `place troops invalid returns clear error`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val baseState = sampleState()
        val state =
            baseState.copy(
                territoryStates =
                    baseState.territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(
                                    territoryId = TerritoryId("alpha"),
                                    ownerId = playerTwo,
                                    troopCount = 3,
                                ),
                        ),
            )

        val exception =
            assertThrows(InvalidMapCommandException::class.java) {
                ruleService.createEvents(
                    state = state,
                    command =
                        PlaceTroopsCommand(
                            lobbyCode = state.lobbyCode,
                            playerId = playerOne,
                            territoryId = TerritoryId("alpha"),
                            troopCount = 1,
                        ),
                )
            }

        assertEquals(
            "Territory 'alpha' gehört nicht Spieler '1', sondern '2'.",
            exception.message,
        )
    }

    @Test
    fun `move troops valid creates deterministic source and target events`() {
        val playerOne = PlayerId(1)
        val state =
            sampleState().copy(
                territoryStates =
                    sampleState().territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(
                                    territoryId = TerritoryId("alpha"),
                                    ownerId = playerOne,
                                    troopCount = 5,
                                ),
                            TerritoryId("beta") to
                                TerritoryState(
                                    territoryId = TerritoryId("beta"),
                                    ownerId = playerOne,
                                    troopCount = 2,
                                ),
                        ),
            )

        val events =
            ruleService.createEvents(
                state = state,
                command =
                    MoveTroopsCommand(
                        lobbyCode = state.lobbyCode,
                        playerId = playerOne,
                        fromTerritoryId = TerritoryId("alpha"),
                        toTerritoryId = TerritoryId("beta"),
                        troopCount = 3,
                    ),
            )

        assertEquals(
            listOf(
                TerritoryTroopsChangedEvent(state.lobbyCode, TerritoryId("alpha"), 2),
                TerritoryTroopsChangedEvent(state.lobbyCode, TerritoryId("beta"), 5),
            ),
            events,
        )
    }

    @Test
    fun `move troops rejects non adjacent territories`() {
        val playerOne = PlayerId(1)
        val state =
            sampleState().copy(
                territoryStates =
                    sampleState().territoryStates +
                        mapOf(
                            TerritoryId("beta") to
                                TerritoryState(
                                    territoryId = TerritoryId("beta"),
                                    ownerId = playerOne,
                                    troopCount = 4,
                                ),
                            TerritoryId("gamma") to
                                TerritoryState(
                                    territoryId = TerritoryId("gamma"),
                                    ownerId = playerOne,
                                    troopCount = 2,
                                ),
                        ),
            )

        val exception =
            assertThrows(InvalidMapCommandException::class.java) {
                ruleService.createEvents(
                    state = state,
                    command =
                        MoveTroopsCommand(
                            lobbyCode = state.lobbyCode,
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("beta"),
                            toTerritoryId = TerritoryId("gamma"),
                            troopCount = 1,
                        ),
                )
            }

        assertEquals(
            "Move von 'beta' nach 'gamma' ist nur für direkt benachbarte Territorien erlaubt.",
            exception.message,
        )
    }

    @Test
    fun `fortify move valid creates applied event and used flag update`() {
        val playerOne = PlayerId(1)
        val state =
            sampleState().copy(
                activePlayer = playerOne,
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.FORTIFY,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                territoryStates =
                    sampleState().territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(TerritoryId("alpha"), playerOne, 5),
                            TerritoryId("beta") to
                                TerritoryState(TerritoryId("beta"), playerOne, 2),
                            TerritoryId("gamma") to
                                TerritoryState(TerritoryId("gamma"), playerOne, 1),
                        ),
            )

        val events =
            ruleService.createEvents(
                state = state,
                command =
                    FortifyMoveCommand(
                        lobbyCode = state.lobbyCode,
                        playerId = playerOne,
                        fromTerritoryId = TerritoryId("alpha"),
                        toTerritoryId = TerritoryId("gamma"),
                        troopCount = 3,
                    ),
            )

        assertEquals(
            listOf(
                FortifyMoveAppliedEvent(
                    lobbyCode = state.lobbyCode,
                    playerId = playerOne,
                    fromTerritoryId = TerritoryId("alpha"),
                    toTerritoryId = TerritoryId("gamma"),
                    troopCount = 3,
                ),
                FortifyUsedSetEvent(
                    lobbyCode = state.lobbyCode,
                    used = true,
                ),
            ),
            events,
        )
    }

    @Test
    fun `fortify move maps validator failures to clear command errors`() {
        val playerOne = PlayerId(1)
        val state =
            sampleState().copy(
                activePlayer = playerOne,
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.FORTIFY,
                        turnCount = 1,
                        startPlayerId = playerOne,
                    ),
                territoryStates =
                    sampleState().territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(TerritoryId("alpha"), playerOne, 1),
                            TerritoryId("beta") to
                                TerritoryState(TerritoryId("beta"), playerOne, 2),
                        ),
            )

        val exception =
            assertThrows(InvalidMapCommandException::class.java) {
                ruleService.createEvents(
                    state = state,
                    command =
                        FortifyMoveCommand(
                            lobbyCode = state.lobbyCode,
                            playerId = playerOne,
                            fromTerritoryId = TerritoryId("alpha"),
                            toTerritoryId = TerritoryId("beta"),
                            troopCount = 1,
                        ),
                )
            }

        assertTrue(exception.message.orEmpty().contains("mindestens eine Truppe"))
    }

    @Test
    fun `attack command creates deterministic resolved conquest event with rng trace`() {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val state = attackState(rngState = 2L, sourceTroops = 5, targetTroops = 2)

        val events =
            ruleService.createEvents(
                state = state,
                command =
                    AttackCommand(
                        lobbyCode = state.lobbyCode,
                        playerId = attacker,
                        fromTerritoryId = TerritoryId("alpha"),
                        toTerritoryId = TerritoryId("beta"),
                        requestedAttackDice = 3,
                        committedTroopCount = 3,
                        occupyingTroopCount = 3,
                    ),
            )

        assertEquals(2, events.size)
        val event = events[0] as AttackResolvedEvent
        val eliminationEvent = events[1] as PlayerEliminatedEvent
        assertEquals(attacker, event.attackerPlayerId)
        assertEquals(defender, event.defenderPlayerId)
        assertEquals(3, event.attackTroops)
        assertEquals(5, event.sourceTroopsBefore)
        assertEquals(2, event.targetTroopsBefore)
        assertEquals(3, event.requestedAttackDice)
        assertEquals(3, event.attackDice)
        assertEquals(2, event.defendDice)
        assertEquals(listOf(5, 3, 4, 1, 2), event.rngTrace)
        assertEquals(listOf(5, 4, 3), event.attackerRolls)
        assertEquals(listOf(2, 1), event.defenderRolls)
        assertEquals(0, event.attackerLosses)
        assertEquals(2, event.defenderLosses)
        assertEquals(5, event.attackerRemaining)
        assertEquals(0, event.defenderRemaining)
        assertEquals(3, event.occupyingTroopCount)
        assertEquals(3, event.minOccupyingTroops)
        assertEquals(2L, event.rngStateBefore)
        assertTrue(event.rngStateAfter != event.rngStateBefore)
        assertEquals(defender, eliminationEvent.playerId)
        assertEquals(attacker, eliminationEvent.eliminatedByPlayerId)
    }

    @Test
    fun `attack without conquest creates deterministic resolved event`() {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val state = attackState(rngState = 19L, sourceTroops = 5, targetTroops = 4)

        val events =
            ruleService.createEvents(
                state = state,
                command =
                    AttackCommand(
                        lobbyCode = state.lobbyCode,
                        playerId = attacker,
                        fromTerritoryId = TerritoryId("alpha"),
                        toTerritoryId = TerritoryId("beta"),
                        requestedAttackDice = 3,
                        committedTroopCount = 3,
                        occupyingTroopCount = 3,
                    ),
            )

        val event = events.single() as AttackResolvedEvent
        assertEquals(attacker, event.attackerPlayerId)
        assertEquals(defender, event.defenderPlayerId)
        assertEquals(3, event.attackTroops)
        assertEquals(5, event.sourceTroopsBefore)
        assertEquals(4, event.targetTroopsBefore)
        assertEquals(listOf(1, 3, 6, 2, 1), event.rngTrace)
        assertEquals(listOf(6, 3, 1), event.attackerRolls)
        assertEquals(listOf(2, 1), event.defenderRolls)
        assertEquals(0, event.attackerLosses)
        assertEquals(2, event.defenderLosses)
        assertEquals(5, event.attackerRemaining)
        assertEquals(2, event.defenderRemaining)
        assertEquals(null, event.occupyingTroopCount)
        assertEquals(null, event.minOccupyingTroops)
        assertEquals(19L, event.rngStateBefore)
        assertTrue(event.rngStateAfter != event.rngStateBefore)
    }

    @Test
    fun `command validation rejects state and actor mismatches`() {
        val playerOne = PlayerId(1)
        val command =
            PlaceTroopsCommand(
                lobbyCode = LobbyCode("ZZ99"),
                playerId = playerOne,
                territoryId = TerritoryId("alpha"),
                troopCount = 1,
            )

        assertMapCommandFailure(
            state = sampleState(),
            command = command,
            expectedMessagePart = "passt nicht zum aktuellen State",
        )
        assertMapCommandFailure(
            state = GameState.initial(LobbyCode("ZZ99")),
            command = command,
            expectedMessagePart = "noch nicht initialisiert",
        )
        assertMapCommandFailure(
            state = sampleState().copy(lobbyCode = LobbyCode("ZZ99")),
            command = command.copy(playerId = PlayerId(99)),
            expectedMessagePart = "ist nicht Teil der Lobby",
        )
        assertMapCommandFailure(
            state = sampleState().copy(lobbyCode = LobbyCode("ZZ99")),
            command = command.copy(territoryId = TerritoryId("missing")),
            expectedMessagePart = "ist nicht Teil der Map",
        )
    }

    @Test
    fun `move troops rejects emptying source territory`() {
        val playerOne = PlayerId(1)
        val state =
            sampleState().withTerritories(
                TerritoryId("alpha") to TerritoryState(TerritoryId("alpha"), playerOne, 3),
                TerritoryId("beta") to TerritoryState(TerritoryId("beta"), playerOne, 1),
            )

        assertMapCommandFailure(
            state = state,
            command =
                MoveTroopsCommand(
                    lobbyCode = state.lobbyCode,
                    playerId = playerOne,
                    fromTerritoryId = TerritoryId("alpha"),
                    toTerritoryId = TerritoryId("beta"),
                    troopCount = 3,
                ),
            expectedMessagePart = "muss mindestens eine Truppe",
        )
    }

    @Test
    fun `attack rejects invalid target ownership troop constraints and missing rng`() {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)

        assertMapCommandFailure(
            state =
                attackState(rngState = 2L, sourceTroops = 5, targetTroops = 2).copy(
                    territoryStates =
                        attackState(rngState = 2L, sourceTroops = 5, targetTroops = 2)
                            .territoryStates +
                            (TerritoryId("beta") to TerritoryState(TerritoryId("beta"), null, 2)),
                ),
            command = attackCommand(attacker = attacker),
            expectedMessagePart = "muss einen Besitzer haben",
        )
        assertMapCommandFailure(
            state =
                attackState(rngState = 2L, sourceTroops = 5, targetTroops = 2).copy(
                    territoryStates =
                        attackState(rngState = 2L, sourceTroops = 5, targetTroops = 2)
                            .territoryStates +
                            (
                                TerritoryId("beta") to
                                    TerritoryState(TerritoryId("beta"), attacker, 2)
                            ),
                ),
            command = attackCommand(attacker = attacker),
            expectedMessagePart = "beide Territorien",
        )
        assertMapCommandFailure(
            state = attackState(rngState = 2L, sourceTroops = 2, targetTroops = 2),
            command = attackCommand(attacker = attacker),
            expectedMessagePart = "mindestens 3 Truppen",
        )
        assertMapCommandFailure(
            state = attackState(rngState = 2L, sourceTroops = 5, targetTroops = 2),
            command = attackCommand(attacker = attacker, committedTroopCount = null),
            expectedMessagePart = "committedTroopCount",
        )
        assertMapCommandFailure(
            state =
                attackState(rngState = 2L, sourceTroops = 5, targetTroops = 2).copy(
                    gameRandomSeed = null,
                    gameRandomState = null,
                ),
            command = attackCommand(attacker = attacker),
            expectedMessagePart = "initialisierten gameRandomSeed",
        )
    }

    @Test
    fun `attack rejects invalid occupation combinations`() {
        val attacker = PlayerId(1)

        val captureState = attackState(rngState = 2L, sourceTroops = 5, targetTroops = 2)
        assertMapCommandFailure(
            state = captureState,
            command =
                attackCommand(
                    attacker = attacker,
                    requestedAttackDice = 3,
                    occupyingTroopCount = 2,
                ),
            expectedMessagePart = "mindestens 3",
        )
        assertMapCommandFailure(
            state = captureState,
            command =
                attackCommand(
                    attacker = attacker,
                    requestedAttackDice = 3,
                    occupyingTroopCount = 5,
                ),
            expectedMessagePart = "darf committedTroopCount nicht überschreiten",
        )
        assertMapCommandFailure(
            state = captureState,
            command =
                attackCommand(
                    attacker = attacker,
                    requestedAttackDice = 3,
                    occupyingTroopCount = 5,
                ),
            expectedMessagePart = "darf committedTroopCount nicht überschreiten",
        )
    }

    @Test
    fun `attack rejects committed troop and rng guard edge cases`() {
        val attacker = PlayerId(1)

        assertMapCommandFailure(
            state = attackState(rngState = 2L, sourceTroops = 5, targetTroops = 2),
            command = attackCommand(attacker = attacker, committedTroopCount = 1),
            expectedMessagePart = "committedTroopCount muss mindestens 2",
        )
        assertMapCommandFailure(
            state = attackState(rngState = 2L, sourceTroops = 4, targetTroops = 2),
            command = attackCommand(attacker = attacker, committedTroopCount = 4),
            expectedMessagePart = "muss mindestens eine Truppe zurücklassen",
        )
        assertMapCommandFailure(
            state =
                attackState(rngState = 2L, sourceTroops = 5, targetTroops = 2).copy(
                    gameRandomState = null,
                ),
            command = attackCommand(attacker = attacker),
            expectedMessagePart = "initialisierten gameRandomState",
        )
    }

    @Test
    fun `spectator cannot execute map commands`() {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val spectatorState =
            sampleState().copy(
                gameStarted = true,
                turnOrder = listOf(attacker),
                territoryStates =
                    sampleState().territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(TerritoryId("alpha"), attacker, 3),
                            TerritoryId("beta") to
                                TerritoryState(TerritoryId("beta"), attacker, 2),
                            TerritoryId("gamma") to
                                TerritoryState(TerritoryId("gamma"), attacker, 1),
                        ),
            )

        val exception =
            assertThrows(InvalidMapCommandException::class.java) {
                ruleService.createEvents(
                    state = spectatorState,
                    command =
                        PlaceTroopsCommand(
                            lobbyCode = spectatorState.lobbyCode,
                            playerId = defender,
                            territoryId = TerritoryId("alpha"),
                            troopCount = 1,
                        ),
                )
            }

        assertEquals("PLAYER_ELIMINATED", exception.reasonCode)
    }

    private fun GameState.withTerritories(
        vararg territories: Pair<TerritoryId, TerritoryState>,
    ): GameState =
        copy(
            territoryStates = territoryStates + territories.toMap(),
        )

    private fun attackState(
        rngState: Long,
        sourceTroops: Int,
        targetTroops: Int,
    ): GameState {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        return sampleState().copy(
            gameRandomSeed = 1L,
            gameRandomState = rngState,
            territoryStates =
                sampleState().territoryStates +
                    mapOf(
                        TerritoryId("alpha") to
                            TerritoryState(
                                territoryId = TerritoryId("alpha"),
                                ownerId = attacker,
                                troopCount = sourceTroops,
                            ),
                        TerritoryId("beta") to
                            TerritoryState(
                                territoryId = TerritoryId("beta"),
                                ownerId = defender,
                                troopCount = targetTroops,
                            ),
                    ),
        )
    }

    private fun attackCommand(
        attacker: PlayerId,
        requestedAttackDice: Int = 3,
        committedTroopCount: Int? = 3,
        occupyingTroopCount: Int? = null,
    ): AttackCommand =
        AttackCommand(
            lobbyCode = LobbyCode("CM12"),
            playerId = attacker,
            fromTerritoryId = TerritoryId("alpha"),
            toTerritoryId = TerritoryId("beta"),
            requestedAttackDice = requestedAttackDice,
            committedTroopCount = committedTroopCount,
            occupyingTroopCount = occupyingTroopCount,
        )

    private fun assertMapCommandFailure(
        state: GameState,
        command: MapCommand,
        expectedMessagePart: String,
    ) {
        val exception =
            assertThrows(InvalidMapCommandException::class.java) {
                ruleService.createEvents(state, command)
            }

        assertTrue(
            exception.message.orEmpty().contains(expectedMessagePart),
            "Expected message to contain '$expectedMessagePart' but was '${exception.message}'.",
        )
    }

    private fun sampleState(): GameState =
        GameState.initial(
            lobbyCode = LobbyCode("CM12"),
            mapDefinition = sampleMapDefinition(),
            players = listOf(PlayerId(1), PlayerId(2)),
        ).copy(gameRandomSeed = 1L, gameRandomState = 1L)

    private fun sampleMapDefinition(): MapDefinition =
        MapDefinition(
            schemaVersion = 1,
            territories =
                listOf(
                    TerritoryDefinition(
                        territoryId = TerritoryId("alpha"),
                        edges =
                            listOf(
                                TerritoryEdgeDefinition(targetId = TerritoryId("beta")),
                                TerritoryEdgeDefinition(targetId = TerritoryId("gamma")),
                            ),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("beta"),
                        edges = listOf(TerritoryEdgeDefinition(targetId = TerritoryId("alpha"))),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("gamma"),
                        edges = listOf(TerritoryEdgeDefinition(targetId = TerritoryId("alpha"))),
                    ),
                ),
            continents =
                listOf(
                    ContinentDefinition(
                        continentId = ContinentId("north"),
                        territoryIds = listOf(TerritoryId("alpha"), TerritoryId("beta")),
                        bonusValue = 3,
                    ),
                ),
        )
}
