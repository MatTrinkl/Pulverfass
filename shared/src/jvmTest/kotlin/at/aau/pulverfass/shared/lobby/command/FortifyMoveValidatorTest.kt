package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TerritoryState
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FortifyMoveValidatorTest {
    private val validator = DefaultFortifyMoveValidator()
    private val lobbyCode = LobbyCode("1154")
    private val playerOne = PlayerId(1)
    private val playerTwo = PlayerId(2)
    private val alpha = TerritoryId("alpha")
    private val beta = TerritoryId("beta")
    private val gamma = TerritoryId("gamma")
    private val delta = TerritoryId("delta")

    @Test
    fun `validate fortify move accepts legal connected own path`() {
        val state =
            runningFortifyState().copy(
                territoryStates =
                    mapOf(
                        alpha to TerritoryState(alpha, playerOne, 4),
                        beta to TerritoryState(beta, playerOne, 2),
                        gamma to TerritoryState(gamma, playerOne, 1),
                        delta to TerritoryState(delta, playerTwo, 5),
                    ),
            )

        val validationError =
            validator.validateFortifyMove(
                state = state,
                playerId = playerOne,
                fromTerritoryId = alpha,
                toTerritoryId = gamma,
                troopCount = 2,
            )

        assertNull(validationError)
    }

    @Test
    fun `validate fortify move returns expected standard errors`() {
        assertEquals(
            FortifyMoveValidationError.NOT_ACTIVE_PLAYER,
            validator.validateFortifyMove(
                state = runningFortifyState(),
                playerId = playerTwo,
                fromTerritoryId = alpha,
                toTerritoryId = beta,
                troopCount = 1,
            ),
        )
        assertEquals(
            FortifyMoveValidationError.WRONG_PHASE,
            validator.validateFortifyMove(
                state =
                    runningFortifyState().copy(
                        turnState =
                            runningFortifyState().turnState?.copy(
                                turnPhase = TurnPhase.ATTACK,
                            ),
                    ),
                playerId = playerOne,
                fromTerritoryId = alpha,
                toTerritoryId = beta,
                troopCount = 1,
            ),
        )
        assertEquals(
            FortifyMoveValidationError.TERRITORY_NOT_OWNED,
            validator.validateFortifyMove(
                state =
                    runningFortifyState().copy(
                        territoryStates =
                            mapOf(
                                alpha to TerritoryState(alpha, playerTwo, 4),
                                beta to TerritoryState(beta, playerOne, 2),
                                gamma to TerritoryState(gamma, playerOne, 1),
                                delta to TerritoryState(delta, playerTwo, 5),
                            ),
                    ),
                playerId = playerOne,
                fromTerritoryId = alpha,
                toTerritoryId = beta,
                troopCount = 1,
            ),
        )
        assertEquals(
            FortifyMoveValidationError.NO_PATH,
            validator.validateFortifyMove(
                state =
                    runningFortifyState().copy(
                        territoryStates =
                            mapOf(
                                alpha to TerritoryState(alpha, playerOne, 4),
                                beta to TerritoryState(beta, playerTwo, 2),
                                gamma to TerritoryState(gamma, playerOne, 1),
                                delta to TerritoryState(delta, playerTwo, 5),
                            ),
                    ),
                playerId = playerOne,
                fromTerritoryId = alpha,
                toTerritoryId = gamma,
                troopCount = 1,
            ),
        )
        assertEquals(
            FortifyMoveValidationError.INSUFFICIENT_TROOPS,
            validator.validateFortifyMove(
                state =
                    runningFortifyState().copy(
                        territoryStates =
                            mapOf(
                                alpha to TerritoryState(alpha, playerOne, 1),
                                beta to TerritoryState(beta, playerOne, 2),
                                gamma to TerritoryState(gamma, playerOne, 1),
                                delta to TerritoryState(delta, playerTwo, 5),
                            ),
                    ),
                playerId = playerOne,
                fromTerritoryId = alpha,
                toTerritoryId = beta,
                troopCount = 1,
            ),
        )
        assertEquals(
            FortifyMoveValidationError.FORTIFY_ALREADY_USED,
            validator.validateFortifyMove(
                state = runningFortifyState().copy(fortifyUsedThisTurn = true),
                playerId = playerOne,
                fromTerritoryId = alpha,
                toTerritoryId = beta,
                troopCount = 1,
            ),
        )
    }

    @Test
    fun `valid fortify targets uses same rule set as validator`() {
        val state =
            runningFortifyState().copy(
                territoryStates =
                    mapOf(
                        alpha to TerritoryState(alpha, playerOne, 4),
                        beta to TerritoryState(beta, playerOne, 2),
                        gamma to TerritoryState(gamma, playerOne, 1),
                        delta to TerritoryState(delta, playerTwo, 5),
                    ),
            )

        assertEquals(
            listOf(beta, gamma),
            validator.validFortifyTargets(
                state = state,
                playerId = playerOne,
                fromTerritoryId = alpha,
            ),
        )
        assertEquals(
            emptyList<TerritoryId>(),
            validator.validFortifyTargets(
                state = state.copy(fortifyUsedThisTurn = true),
                playerId = playerOne,
                fromTerritoryId = alpha,
            ),
        )
        assertEquals(
            emptyList<TerritoryId>(),
            validator.validFortifyTargets(
                state = state.copy(turnState = state.turnState?.copy(turnPhase = TurnPhase.ATTACK)),
                playerId = playerOne,
                fromTerritoryId = alpha,
            ),
        )
        assertEquals(
            emptyList<TerritoryId>(),
            validator.validFortifyTargets(
                state =
                    state.copy(
                        activePlayer = playerTwo,
                        turnState = state.turnState?.copy(activePlayerId = playerTwo),
                    ),
                playerId = playerOne,
                fromTerritoryId = alpha,
            ),
        )
    }

    private fun runningFortifyState(): GameState =
        GameState.initial(
            lobbyCode = lobbyCode,
            mapDefinition = sampleMapDefinition(),
            players = listOf(playerOne, playerTwo),
        ).copy(
            activePlayer = playerOne,
            turnOrder = listOf(playerOne, playerTwo),
            turnNumber = 1,
            turnState =
                TurnState(
                    activePlayerId = playerOne,
                    turnPhase = TurnPhase.FORTIFY,
                    turnCount = 1,
                    startPlayerId = playerOne,
                ),
            status = GameStatus.RUNNING,
        )

    private fun sampleMapDefinition(): MapDefinition =
        MapDefinition(
            schemaVersion = 1,
            territories =
                listOf(
                    TerritoryDefinition(
                        territoryId = alpha,
                        edges = listOf(TerritoryEdgeDefinition(beta)),
                    ),
                    TerritoryDefinition(
                        territoryId = beta,
                        edges =
                            listOf(
                                TerritoryEdgeDefinition(alpha),
                                TerritoryEdgeDefinition(gamma),
                            ),
                    ),
                    TerritoryDefinition(
                        territoryId = gamma,
                        edges = listOf(TerritoryEdgeDefinition(beta)),
                    ),
                    TerritoryDefinition(
                        territoryId = delta,
                        edges = emptyList(),
                    ),
                ),
            continents =
                listOf(
                    ContinentDefinition(
                        continentId = ContinentId("north"),
                        territoryIds = listOf(alpha, beta, gamma, delta),
                        bonusValue = 3,
                    ),
                ),
        )
}
