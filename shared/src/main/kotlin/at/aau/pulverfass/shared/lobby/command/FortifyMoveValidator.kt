package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TurnPhase

/**
 * Fachliche Fehlercodes für die Validierung eines Fortify-Zugs.
 */
enum class FortifyMoveValidationError {
    NOT_ACTIVE_PLAYER,
    WRONG_PHASE,
    TERRITORY_NOT_OWNED,
    NO_PATH,
    INSUFFICIENT_TROOPS,
    FORTIFY_ALREADY_USED,
}

/**
 * Prüft, ob ein Spieler in der aktuellen Spielsituation Truppen zwischen zwei eigenen
 * Territorien verschieben darf.
 */
interface FortifyMoveValidator {
    /**
     * Validiert einen konkreten Fortify-Zug.
     *
     * @return `null`, wenn der Zug erlaubt ist, sonst der fachliche Ablehnungsgrund
     */
    fun validateFortifyMove(
        state: GameState,
        playerId: PlayerId,
        fromTerritoryId: TerritoryId,
        toTerritoryId: TerritoryId,
        troopCount: Int,
    ): FortifyMoveValidationError?

    /**
     * Ermittelt alle legal erreichbaren Zielterritorien für ein Ausgangsterritorium.
     */
    fun validFortifyTargets(
        state: GameState,
        playerId: PlayerId,
        fromTerritoryId: TerritoryId,
    ): List<TerritoryId>
}

/**
 * Standardimplementierung der Fortify-Regeln auf Basis des aktuellen [GameState].
 */
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
    ): List<TerritoryId> = state.validFortifyTargets(playerId, fromTerritoryId)
}
