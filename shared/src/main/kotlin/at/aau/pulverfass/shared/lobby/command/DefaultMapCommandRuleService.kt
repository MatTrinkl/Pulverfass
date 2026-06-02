package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.battle.BattleOutcome
import at.aau.pulverfass.shared.lobby.battle.BattleResolver
import at.aau.pulverfass.shared.lobby.battle.BattleRngFactory
import at.aau.pulverfass.shared.lobby.battle.RiskLikeBattleResolverV1
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.state.GameState

/**
 * Standard-Regelservice für Map-bezogene Domain-Commands.
 */
class DefaultMapCommandRuleService(
    private val battleResolver: BattleResolver = RiskLikeBattleResolverV1(),
    private val fortifyMoveValidator: FortifyMoveValidator = DefaultFortifyMoveValidator(),
) : MapCommandRuleService {
    override fun createEvents(
        state: GameState,
        command: MapCommand,
    ): List<LobbyEvent> {
        requireSameLobby(state, command)
        requireMapLoaded(state)
        requireKnownPlayer(state, command.playerId)
        requireActiveMatchParticipant(state, command.playerId)

        return when (command) {
            is PlaceTroopsCommand -> createPlaceTroopsEvents(state, command)
            is MoveTroopsCommand -> createMoveTroopsEvents(state, command)
            is FortifyMoveCommand -> createFortifyMoveEvents(state, command)
            is AttackCommand -> createAttackEvents(state, command)
        }
    }

    private fun createPlaceTroopsEvents(
        state: GameState,
        command: PlaceTroopsCommand,
    ): List<LobbyEvent> {
        requireOwnedTerritory(state, command.playerId, command.territoryId)

        return listOf(
            TerritoryTroopsChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.territoryId,
                troopCount =
                    state.troopCountOf(command.territoryId) + command.troopCount,
            ),
        )
    }

    private fun createMoveTroopsEvents(
        state: GameState,
        command: MoveTroopsCommand,
    ): List<LobbyEvent> {
        requireOwnedTerritory(state, command.playerId, command.fromTerritoryId)
        requireOwnedTerritory(state, command.playerId, command.toTerritoryId)
        requireAdjacent(state, command.fromTerritoryId, command.toTerritoryId, "Move")

        val sourceTroops = state.troopCountOf(command.fromTerritoryId)
        if (sourceTroops <= command.troopCount) {
            throw InvalidMapCommandException(
                "Move von '${command.fromTerritoryId.value}' nach " +
                    "'${command.toTerritoryId.value}' muss mindestens " +
                    "eine Truppe zurücklassen: vorhanden=$sourceTroops, " +
                    "bewegt=${command.troopCount}.",
            )
        }

        return listOf(
            TerritoryTroopsChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.fromTerritoryId,
                troopCount = sourceTroops - command.troopCount,
            ),
            TerritoryTroopsChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.toTerritoryId,
                troopCount =
                    state.troopCountOf(command.toTerritoryId) + command.troopCount,
            ),
        )
    }

    private fun createFortifyMoveEvents(
        state: GameState,
        command: FortifyMoveCommand,
    ): List<LobbyEvent> {
        val validationError =
            fortifyMoveValidator.validateFortifyMove(
                state = state,
                playerId = command.playerId,
                fromTerritoryId = command.fromTerritoryId,
                toTerritoryId = command.toTerritoryId,
                troopCount = command.troopCount,
            )
        if (validationError != null) {
            throw InvalidMapCommandException(
                fortifyValidationErrorMessage(
                    state = state,
                    command = command,
                    error = validationError,
                ),
            )
        }

        return listOf(
            FortifyMoveAppliedEvent(
                lobbyCode = command.lobbyCode,
                playerId = command.playerId,
                fromTerritoryId = command.fromTerritoryId,
                toTerritoryId = command.toTerritoryId,
                troopCount = command.troopCount,
            ),
            FortifyUsedSetEvent(
                lobbyCode = command.lobbyCode,
                used = true,
            ),
        )
    }

    private fun createAttackEvents(
        state: GameState,
        command: AttackCommand,
    ): List<LobbyEvent> {
        requireOwnedTerritory(state, command.playerId, command.fromTerritoryId)
        requireKnownTerritory(state, command.toTerritoryId)
        requireAdjacent(state, command.fromTerritoryId, command.toTerritoryId, "Attack")

        val defenderId = requireDefenderId(state, command)
        val sourceTroops = requireAttackReadySourceTroops(state, command)
        val targetTroops = state.troopCountOf(command.toTerritoryId)
        val committedTroopCount = requireCommittedTroopCount(command, sourceTroops)
        val rngState = requireGameRandomState(state)

        val rng = BattleRngFactory.fromState(rngState)
        val outcome =
            battleResolver.resolve(
                attackTroops = sourceTroops,
                defendTroops = targetTroops,
                requestedAttackDice = command.requestedAttackDice,
                rng = rng,
            )
        val resolvedOccupyingTroopCount =
            resolveOccupyingTroopCount(command, outcome, committedTroopCount)
        val resolvedEvent =
            buildAttackResolvedEvent(
                command = command,
                defenderId = defenderId,
                sourceTroops = sourceTroops,
                targetTroops = targetTroops,
                committedTroopCount = committedTroopCount,
                outcome = outcome,
                rngState = rngState,
                rngStateAfter = rng.snapshotState(),
                occupyingTroopCount = resolvedOccupyingTroopCount,
            )

        return listOfNotNull(
            resolvedEvent,
            buildEliminationEvent(state, command, defenderId, resolvedEvent),
        )
    }

    private fun requireDefenderId(
        state: GameState,
        command: AttackCommand,
    ): PlayerId {
        val defenderId =
            state.ownerOf(command.toTerritoryId)
                ?: throw InvalidMapCommandException(
                    "Attack-Ziel '${command.toTerritoryId.value}' muss einen Besitzer haben.",
                )
        if (defenderId == command.playerId) {
            throw InvalidMapCommandException(
                "Attack von '${command.fromTerritoryId.value}' nach " +
                    "'${command.toTerritoryId.value}' ist ungültig, " +
                    "da beide Territorien Spieler " +
                    "'${command.playerId.value}' gehören.",
            )
        }
        return defenderId
    }

    private fun requireAttackReadySourceTroops(
        state: GameState,
        command: AttackCommand,
    ): Int {
        val sourceTroops = state.troopCountOf(command.fromTerritoryId)
        if (sourceTroops < MIN_SOURCE_TROOPS_FOR_ATTACK) {
            throw InvalidMapCommandException(
                "Attack von '${command.fromTerritoryId.value}' benötigt mindestens " +
                    "$MIN_SOURCE_TROOPS_FOR_ATTACK Truppen, " +
                    "vorhanden sind $sourceTroops.",
            )
        }
        return sourceTroops
    }

    private fun requireCommittedTroopCount(
        command: AttackCommand,
        sourceTroops: Int,
    ): Int {
        val committedTroopCount =
            command.committedTroopCount
                ?: throw InvalidMapCommandException(
                    "Attack benötigt committedTroopCount für ein replayfaehiges Ergebnis-Event.",
                )
        if (committedTroopCount < MIN_ATTACK_COMMITTED_TROOPS) {
            throw InvalidMapCommandException(
                "AttackCommand.committedTroopCount muss mindestens " +
                    "$MIN_ATTACK_COMMITTED_TROOPS sein, war aber " +
                    "$committedTroopCount.",
            )
        }
        if (committedTroopCount > sourceTroops - 1) {
            throw InvalidMapCommandException(
                "Attack von '${command.fromTerritoryId.value}' muss mindestens eine Truppe " +
                    "zurücklassen: vorhanden=$sourceTroops, committed=$committedTroopCount.",
            )
        }
        if (command.requestedAttackDice > committedTroopCount) {
            throw InvalidMapCommandException(
                "AttackCommand.requestedAttackDice darf committedTroopCount nicht " +
                    "überschreiten: dice=${command.requestedAttackDice}, " +
                    "committed=$committedTroopCount.",
            )
        }
        return committedTroopCount
    }

    private fun requireGameRandomState(state: GameState): Long {
        state.gameRandomSeed
            ?: throw InvalidMapCommandException(
                "Attack benötigt einen initialisierten gameRandomSeed.",
            )
        return state.gameRandomState
            ?: throw InvalidMapCommandException(
                "Attack benötigt einen initialisierten gameRandomState.",
            )
    }

    private fun resolveOccupyingTroopCount(
        command: AttackCommand,
        outcome: BattleOutcome,
        committedTroopCount: Int,
    ): Int? {
        if (!outcome.capture) {
            return null
        }

        val minOccupyingTroops =
            outcome.minOccupyingTroops
                ?: throw InvalidMapCommandException(
                    "Capture-Outcome benötigt minOccupyingTroops.",
                )
        val resolvedOccupyingTroopCount = command.occupyingTroopCount ?: committedTroopCount
        if (resolvedOccupyingTroopCount < minOccupyingTroops) {
            throw InvalidMapCommandException(
                "occupyingTroopCount muss mindestens $minOccupyingTroops sein, war aber " +
                    "$resolvedOccupyingTroopCount.",
                reasonCode = "INVALID_MOVE_AFTER_CAPTURE",
            )
        }
        if (resolvedOccupyingTroopCount > committedTroopCount) {
            throw InvalidMapCommandException(
                "occupyingTroopCount darf committedTroopCount nicht überschreiten: " +
                    "occupying=$resolvedOccupyingTroopCount, committed=$committedTroopCount.",
                reasonCode = "INVALID_MOVE_AFTER_CAPTURE",
            )
        }
        if (resolvedOccupyingTroopCount >= outcome.attackerRemaining) {
            throw InvalidMapCommandException(
                "Attack-Eroberung von '${command.toTerritoryId.value}' muss mindestens " +
                    "eine Truppe auf '${command.fromTerritoryId.value}' zurücklassen.",
                reasonCode = "INVALID_MOVE_AFTER_CAPTURE",
            )
        }
        return resolvedOccupyingTroopCount
    }

    private fun buildAttackResolvedEvent(
        command: AttackCommand,
        defenderId: PlayerId,
        sourceTroops: Int,
        targetTroops: Int,
        committedTroopCount: Int,
        outcome: BattleOutcome,
        rngState: Long,
        rngStateAfter: Long,
        occupyingTroopCount: Int?,
    ): AttackResolvedEvent =
        AttackResolvedEvent(
            lobbyCode = command.lobbyCode,
            attackerPlayerId = command.playerId,
            defenderPlayerId = defenderId,
            fromTerritoryId = command.fromTerritoryId,
            toTerritoryId = command.toTerritoryId,
            attackTroops = committedTroopCount,
            sourceTroopsBefore = sourceTroops,
            targetTroopsBefore = targetTroops,
            requestedAttackDice = command.requestedAttackDice,
            attackDice = outcome.attackDice,
            defendDice = outcome.defendDice,
            attackerRolls = outcome.attackerRolls,
            defenderRolls = outcome.defenderRolls,
            rngTrace = outcome.rngTrace,
            rngStateBefore = rngState,
            rngStateAfter = rngStateAfter,
            attackerLosses = outcome.attackerLosses,
            defenderLosses = outcome.defenderLosses,
            attackerRemaining = outcome.attackerRemaining,
            defenderRemaining = outcome.defenderRemaining,
            occupyingTroopCount = occupyingTroopCount,
            minOccupyingTroops = outcome.minOccupyingTroops,
        )

    private fun buildEliminationEvent(
        state: GameState,
        command: AttackCommand,
        defenderId: PlayerId,
        resolvedEvent: AttackResolvedEvent,
    ): PlayerEliminatedEvent? =
        if (resolvedEvent.capture && state.ownedTerritoryCount(defenderId) == 1) {
            PlayerEliminatedEvent(
                lobbyCode = command.lobbyCode,
                playerId = defenderId,
                eliminatedByPlayerId = command.playerId,
            )
        } else {
            null
        }

    private fun requireSameLobby(
        state: GameState,
        command: MapCommand,
    ) {
        if (state.lobbyCode != command.lobbyCode) {
            throw InvalidMapCommandException(
                "MapCommand für Lobby '${command.lobbyCode.value}' passt nicht zum aktuellen " +
                    "State '${state.lobbyCode.value}'.",
            )
        }
    }

    private fun requireMapLoaded(state: GameState) {
        if (!state.hasMap()) {
            throw InvalidMapCommandException(
                "Map-State ist für Lobby '${state.lobbyCode.value}' noch nicht initialisiert.",
            )
        }
    }

    private fun requireKnownPlayer(
        state: GameState,
        playerId: PlayerId,
    ) {
        if (!state.hasPlayer(playerId)) {
            throw InvalidMapCommandException(
                "Spieler '${playerId.value}' ist nicht Teil der Lobby '${state.lobbyCode.value}'.",
            )
        }
    }

    private fun requireActiveMatchParticipant(
        state: GameState,
        playerId: PlayerId,
    ) {
        if (state.isSpectator(playerId)) {
            throw InvalidMapCommandException(
                "Spieler '${playerId.value}' ist eliminiert und kann keine Match-Aktionen " +
                    "mehr ausführen.",
                reasonCode = "PLAYER_ELIMINATED",
            )
        }
    }

    private fun requireOwnedTerritory(
        state: GameState,
        playerId: PlayerId,
        territoryId: TerritoryId,
    ) {
        requireKnownTerritory(state, territoryId)

        val ownerId = state.ownerOf(territoryId)
        if (ownerId != playerId) {
            throw InvalidMapCommandException(
                "Territory '${territoryId.value}' gehört nicht Spieler " +
                    "'${playerId.value}', sondern '${ownerId?.value}'.",
            )
        }
    }

    private fun requireKnownTerritory(
        state: GameState,
        territoryId: TerritoryId,
    ) {
        if (state.territoryStateOf(territoryId) == null) {
            throw InvalidMapCommandException(
                "Territory '${territoryId.value}' ist nicht Teil der Map von Lobby " +
                    "'${state.lobbyCode.value}'.",
            )
        }
    }

    private fun requireAdjacent(
        state: GameState,
        fromTerritoryId: TerritoryId,
        toTerritoryId: TerritoryId,
        actionName: String,
    ) {
        if (!state.isAdjacent(fromTerritoryId, toTerritoryId)) {
            throw InvalidMapCommandException(
                "$actionName von '${fromTerritoryId.value}' nach '${toTerritoryId.value}' " +
                    "ist nur für direkt benachbarte Territorien erlaubt.",
            )
        }
    }

    private fun fortifyValidationErrorMessage(
        state: GameState,
        command: FortifyMoveCommand,
        error: FortifyMoveValidationError,
    ): String =
        when (error) {
            FortifyMoveValidationError.NOT_ACTIVE_PLAYER ->
                "Fortify ist nur für den aktiven Spieler erlaubt. Aktiv ist " +
                    "'${state.activePlayer?.value}', angefordert wurde '${command.playerId.value}'."
            FortifyMoveValidationError.WRONG_PHASE ->
                "Fortify ist nur in Phase 'FORTIFY' erlaubt, aktuell ist " +
                    "'${state.activeTurnPhase?.name}'."
            FortifyMoveValidationError.TERRITORY_NOT_OWNED ->
                "Fortify von '${command.fromTerritoryId.value}' nach " +
                    "'${command.toTerritoryId.value}' erfordert zwei eigene Territorien."
            FortifyMoveValidationError.NO_PATH ->
                "Fortify von '${command.fromTerritoryId.value}' nach " +
                    "'${command.toTerritoryId.value}' benötigt einen zusammenhängenden " +
                    "Pfad über eigene Gebiete."
            FortifyMoveValidationError.INSUFFICIENT_TROOPS ->
                "Fortify von '${command.fromTerritoryId.value}' nach " +
                    "'${command.toTerritoryId.value}' muss mindestens eine Truppe " +
                    "zurücklassen: vorhanden=${state.troopCountOf(command.fromTerritoryId)}, " +
                    "bewegt=${command.troopCount}."
            FortifyMoveValidationError.FORTIFY_ALREADY_USED ->
                "Fortify wurde in diesem Zug bereits verwendet."
        }
}
