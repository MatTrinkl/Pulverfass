package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TurnPhase

enum class FortifyMoveValidationError {
    NOT_ACTIVE_PLAYER,
    WRONG_PHASE,
    TERRITORY_NOT_OWNED,
    NO_PATH,
    INSUFFICIENT_TROOPS,
    FORTIFY_ALREADY_USED,
}

interface FortifyMoveValidator {
    fun validateFortifyMove(
        state: GameState,
        playerId: PlayerId,
        fromTerritoryId: TerritoryId,
        toTerritoryId: TerritoryId,
        troopCount: Int,
    ): FortifyMoveValidationError?

    fun validFortifyTargets(
        state: GameState,
        playerId: PlayerId,
        fromTerritoryId: TerritoryId,
    ): List<TerritoryId>
}

class DefaultFortifyMoveValidator : FortifyMoveValidator {
    override fun validateFortifyMove(
        state: GameState,
        playerId: PlayerId,
        fromTerritoryId: TerritoryId,
        toTerritoryId: TerritoryId,
        troopCount: Int,
    ): FortifyMoveValidationError? {
        if (state.activePlayer != playerId) {
            return FortifyMoveValidationError.NOT_ACTIVE_PLAYER
        }
        if (state.activeTurnPhase != TurnPhase.FORTIFY) {
            return FortifyMoveValidationError.WRONG_PHASE
        }
        if (state.fortifyUsedThisTurn) {
            return FortifyMoveValidationError.FORTIFY_ALREADY_USED
        }
        if (
            state.territoryStateOf(fromTerritoryId)?.ownerId != playerId ||
            state.territoryStateOf(toTerritoryId)?.ownerId != playerId
        ) {
            return FortifyMoveValidationError.TERRITORY_NOT_OWNED
        }
        val sourceTroopCount = state.territoryStateOf(fromTerritoryId)?.troopCount
        if (sourceTroopCount == null || troopCount < 1 || sourceTroopCount < troopCount + 1) {
            return FortifyMoveValidationError.INSUFFICIENT_TROOPS
        }
        if (
            fromTerritoryId == toTerritoryId ||
            !state.canFortifyMove(playerId, fromTerritoryId, toTerritoryId)
        ) {
            return FortifyMoveValidationError.NO_PATH
        }

        return null
    }

    override fun validFortifyTargets(
        state: GameState,
        playerId: PlayerId,
        fromTerritoryId: TerritoryId,
    ): List<TerritoryId> {
        if (state.activePlayer != playerId) {
            return emptyList()
        }
        if (state.activeTurnPhase != TurnPhase.FORTIFY) {
            return emptyList()
        }
        if (state.fortifyUsedThisTurn) {
            return emptyList()
        }
        val sourceState = state.territoryStateOf(fromTerritoryId) ?: return emptyList()
        if (sourceState.ownerId != playerId) {
            return emptyList()
        }
        if (sourceState.troopCount < 2) {
            return emptyList()
        }

        return state
            .territoriesOwnedBy(playerId)
            .map { territoryState -> territoryState.territoryId }
            .filterNot { territoryId -> territoryId == fromTerritoryId }
            .filter { territoryId ->
                state.canFortifyMove(playerId, fromTerritoryId, territoryId)
            }
    }
}
