package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
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
    fun `attack command creates deterministic conquest events`() {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val state =
            sampleState().copy(
                territoryStates =
                    sampleState().territoryStates +
                        mapOf(
                            TerritoryId("alpha") to
                                TerritoryState(
                                    territoryId = TerritoryId("alpha"),
                                    ownerId = attacker,
                                    troopCount = 5,
                                ),
                            TerritoryId("beta") to
                                TerritoryState(
                                    territoryId = TerritoryId("beta"),
                                    ownerId = defender,
                                    troopCount = 2,
                                ),
                        ),
            )

        val events =
            ruleService.createEvents(
                state = state,
                command =
                    AttackCommand(
                        lobbyCode = state.lobbyCode,
                        playerId = attacker,
                        fromTerritoryId = TerritoryId("alpha"),
                        toTerritoryId = TerritoryId("beta"),
                        attackerLosses = 1,
                        defenderLosses = 2,
                        occupyingTroopCount = 2,
                    ),
            )

        assertEquals(
            listOf(
                TerritoryTroopsChangedEvent(state.lobbyCode, TerritoryId("alpha"), 2),
                TerritoryOwnerChangedEvent(state.lobbyCode, TerritoryId("beta"), attacker),
                TerritoryTroopsChangedEvent(state.lobbyCode, TerritoryId("beta"), 2),
            ),
            events,
        )
    }

    @Test
    fun `attack without conquest updates both troop counts only`() {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val state =
            sampleState().withTerritories(
                TerritoryId("alpha") to TerritoryState(TerritoryId("alpha"), attacker, 5),
                TerritoryId("beta") to TerritoryState(TerritoryId("beta"), defender, 4),
            )

        val events =
            ruleService.createEvents(
                state = state,
                command =
                    AttackCommand(
                        lobbyCode = state.lobbyCode,
                        playerId = attacker,
                        fromTerritoryId = TerritoryId("alpha"),
                        toTerritoryId = TerritoryId("beta"),
                        attackerLosses = 1,
                        defenderLosses = 2,
                    ),
            )

        assertEquals(
            listOf(
                TerritoryTroopsChangedEvent(state.lobbyCode, TerritoryId("alpha"), 4),
                TerritoryTroopsChangedEvent(state.lobbyCode, TerritoryId("beta"), 2),
            ),
            events,
        )
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
    fun `attack rejects invalid target ownership and troop constraints`() {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)

        assertMapCommandFailure(
            state =
                sampleState().withTerritories(
                    TerritoryId("alpha") to TerritoryState(TerritoryId("alpha"), attacker, 5),
                    TerritoryId("beta") to TerritoryState(TerritoryId("beta"), null, 2),
                ),
            command = attackCommand(attacker = attacker, defenderLosses = 1),
            expectedMessagePart = "muss einen Besitzer haben",
        )
        assertMapCommandFailure(
            state =
                sampleState().withTerritories(
                    TerritoryId("alpha") to TerritoryState(TerritoryId("alpha"), attacker, 5),
                    TerritoryId("beta") to TerritoryState(TerritoryId("beta"), attacker, 2),
                ),
            command = attackCommand(attacker = attacker, defenderLosses = 1),
            expectedMessagePart = "beide Territorien",
        )
        assertMapCommandFailure(
            state =
                sampleState().withTerritories(
                    TerritoryId("alpha") to TerritoryState(TerritoryId("alpha"), attacker, 1),
                    TerritoryId("beta") to TerritoryState(TerritoryId("beta"), defender, 2),
                ),
            command = attackCommand(attacker = attacker, defenderLosses = 1),
            expectedMessagePart = "mindestens 2 Truppen",
        )
        assertMapCommandFailure(
            state =
                sampleState().withTerritories(
                    TerritoryId("alpha") to TerritoryState(TerritoryId("alpha"), attacker, 5),
                    TerritoryId("beta") to TerritoryState(TerritoryId("beta"), defender, 2),
                ),
            command = attackCommand(attacker = attacker, defenderLosses = 3),
            expectedMessagePart = "mehr Verteidiger entfernen",
        )
    }

    @Test
    fun `attack rejects invalid occupation combinations`() {
        val attacker = PlayerId(1)
        val defender = PlayerId(2)
        val state =
            sampleState().withTerritories(
                TerritoryId("alpha") to TerritoryState(TerritoryId("alpha"), attacker, 3),
                TerritoryId("beta") to TerritoryState(TerritoryId("beta"), defender, 3),
            )

        assertMapCommandFailure(
            state = state,
            command =
                attackCommand(
                    attacker = attacker,
                    attackerLosses = 1,
                    defenderLosses = 1,
                    occupyingTroopCount = 1,
                ),
            expectedMessagePart = "darf keine occupyingTroopCount",
        )
        assertMapCommandFailure(
            state = state,
            command =
                attackCommand(
                    attacker = attacker,
                    attackerLosses = 3,
                    defenderLosses = 1,
                ),
            expectedMessagePart = "leer",
        )
        assertMapCommandFailure(
            state = state,
            command =
                attackCommand(
                    attacker = attacker,
                    attackerLosses = 0,
                    defenderLosses = 3,
                ),
            expectedMessagePart = "occupyingTroopCount",
        )
        assertMapCommandFailure(
            state = state,
            command =
                attackCommand(
                    attacker = attacker,
                    attackerLosses = 1,
                    defenderLosses = 3,
                    occupyingTroopCount = 2,
                ),
            expectedMessagePart = "muss mindestens eine",
        )
    }

    private fun GameState.withTerritories(
        vararg territories: Pair<TerritoryId, TerritoryState>,
    ): GameState =
        copy(
            territoryStates = territoryStates + territories.toMap(),
        )

    private fun attackCommand(
        attacker: PlayerId,
        attackerLosses: Int = 0,
        defenderLosses: Int,
        occupyingTroopCount: Int? = null,
    ): AttackCommand =
        AttackCommand(
            lobbyCode = LobbyCode("CM12"),
            playerId = attacker,
            fromTerritoryId = TerritoryId("alpha"),
            toTerritoryId = TerritoryId("beta"),
            attackerLosses = attackerLosses,
            defenderLosses = defenderLosses,
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
        )

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
