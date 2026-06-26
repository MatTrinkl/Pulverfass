package at.aau.pulverfass.shared.lobby.reducer

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardDrawnEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.CheatReinforcementBonusUsedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.FortifyUsedSetEvent
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
import at.aau.pulverfass.shared.lobby.normalizePlayerDisplayNameOrFallback
import at.aau.pulverfass.shared.lobby.state.CardDeckFactory
import at.aau.pulverfass.shared.lobby.state.CardSetValidator
import at.aau.pulverfass.shared.lobby.state.DiscardPileState
import at.aau.pulverfass.shared.lobby.state.GameStartPreparation
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TradeInProgression
import at.aau.pulverfass.shared.lobby.state.TurnOrderPolicy
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.lobby.state.TurnStateMachine

/**
 * Standard-Reducer für die minimalen Lobby-Events der ersten Domain-Schicht.
 *
 * Der Reducer arbeitet rein funktional und liefert immer eine neue State-Kopie
 * zurück. Ungültige Zustandsübergänge werden nicht still ignoriert, sondern als
 * [InvalidLobbyEventException] sichtbar gemacht.
 */
class DefaultLobbyEventReducer : LobbyEventReducer {
    override fun apply(
        state: GameState,
        event: LobbyEvent,
        context: EventContext?,
    ): GameState {
        if (event.lobbyCode != state.lobbyCode) {
            throw LobbyCodeMismatchException(
                expectedLobbyCode = state.lobbyCode,
                actualLobbyCode = event.lobbyCode,
            )
        }

        val updatedState =
            when (event) {
                is InvalidActionDetected ->
                    state.copy(
                        lastInvalidActionReason = event.reason,
                    )

                is LobbyClosed ->
                    state.copy(
                        status = GameStatus.CLOSED,
                        activePlayer = null,
                        pendingReinforcements = null,
                        turnState = null,
                        closedReason = event.reason,
                    )

                is LobbyCreated ->
                    state.copy(
                        status = GameStatus.WAITING_FOR_PLAYERS,
                        closedReason = null,
                        winnerPlayerId = null,
                        pendingReinforcements = null,
                    )

                is AttackResolvedEvent -> onAttackResolved(state, event)
                is CardDrawnEvent -> onCardDrawn(state, event)
                is CardSetTradedInEvent -> onCardSetTradedIn(state, event)
                is MatchEndedEvent -> onMatchEnded(state, event)
                is CheatReinforcementBonusUsedEvent -> onCheatReinforcementBonusUsed(state, event)
                is PendingReinforcementsChangedEvent ->
                    onPendingReinforcementsChanged(state, event)
                is PendingReinforcementsSetEvent -> onPendingReinforcementsSet(state, event)
                is PlayerEliminatedEvent -> onPlayerEliminated(state, event)
                is PlayerCardsRemovedEvent -> onPlayerCardsRemoved(state, event)
                is PlayerJoined -> onPlayerJoined(state, event.playerId, event.playerDisplayName)
                is PlayerLeft -> onPlayerLeft(state, event.playerId)
                is PlayerKicked ->
                    onPlayerKicked(
                        state,
                        event.targetPlayerId,
                        event.requesterPlayerId,
                    )
                is StartPlayerConfigured ->
                    onStartPlayerConfigured(
                        state = state,
                        startPlayerId = event.startPlayerId,
                        requesterPlayerId = event.requesterPlayerId,
                    )
                is FortifyMoveAppliedEvent -> onFortifyMoveApplied(state, event)
                is FortifyUsedSetEvent -> state.copy(fortifyUsedThisTurn = event.used)
                is GameStarted -> onGameStarted(state, event)
                is SystemTick -> state
                is TerritoryOwnerChangedEvent -> onTerritoryOwnerChanged(state, event)
                is TerritoryTroopsChangedEvent -> onTerritoryTroopsChanged(state, event)
                is TimeoutTriggered -> state
                is TurnEnded -> onTurnEnded(state, event.playerId)
                is TurnStateUpdatedEvent -> onTurnStateUpdated(state, event)
            }

        return updatedState.withMetadata(context)
    }

    private fun onPlayerJoined(
        state: GameState,
        playerId: PlayerId,
        playerDisplayName: String,
    ): GameState {
        if (state.players.contains(playerId)) {
            throw InvalidLobbyEventException(
                "Player '$playerId' ist bereits Teil der Lobby '${state.lobbyCode}'.",
            )
        }

        val updatedPlayers = state.players + playerId
        val updatedTurnOrder = TurnOrderPolicy.normalize(state.turnOrder + playerId)
        val updatedLobbyOwner = state.lobbyOwner ?: playerId
        val updatedStatus = preserveLobbyStatus(state, updatedPlayers)
        val updatedTurnState =
            synchronizedTurnStateForLobbySetup(state, updatedTurnOrder, state.turnOrder)
        val normalizedPlayerDisplayName =
            normalizePlayerDisplayNameOrFallback(playerDisplayName)
        val baseUpdatedState =
            state.copy(
                players = updatedPlayers,
                playerDisplayNames =
                    state.playerDisplayNames + (playerId to normalizedPlayerDisplayName),
                lobbyOwner = updatedLobbyOwner,
                pendingReinforcements = state.pendingReinforcements,
                setupTroopsToPlaceByPlayer = state.setupTroopsToPlaceByPlayer + (playerId to 0),
                tradeRequiredOnNextReinforcementPhaseByPlayer =
                    state.tradeRequiredOnNextReinforcementPhaseByPlayer + (playerId to false),
                configuredStartPlayerId = updatedTurnState?.startPlayerId,
                turnOrder = updatedTurnOrder,
                status = updatedStatus,
            )

        return updatedTurnState?.let {
            applyTurnStateUpdate(baseUpdatedState, turnStateUpdatedEvent(baseUpdatedState, it))
        }
            ?: baseUpdatedState.copy(
                activePlayer = null,
                turnNumber = 0,
                turnState = null,
            )
    }

    private fun onPlayerLeft(
        state: GameState,
        playerId: PlayerId,
    ): GameState {
        if (!state.players.contains(playerId)) {
            throw InvalidLobbyEventException(
                "Player '$playerId' ist nicht Teil der Lobby '${state.lobbyCode}'.",
            )
        }

        val updatedPlayers = state.players.filterNot { it == playerId }
        val updatedTurnOrder = state.turnOrder.filterNot { it == playerId }
        val updatedLobbyOwner =
            if (state.lobbyOwner == playerId) {
                updatedPlayers.firstOrNull()
            } else {
                state.lobbyOwner
            }
        val updatedStatus = preserveLobbyStatus(state, updatedPlayers)
        val updatedTurnState =
            synchronizedTurnStateForLobbySetup(
                state = state,
                turnOrder = updatedTurnOrder,
                previousTurnOrder = state.turnOrder,
                removedPlayerId = playerId,
            )
        val baseUpdatedState =
            state.copy(
                players = updatedPlayers,
                playerDisplayNames = state.playerDisplayNames - playerId,
                lobbyOwner = updatedLobbyOwner,
                activePlayer = state.activePlayer?.takeIf(updatedPlayers::contains),
                pendingReinforcements =
                    state.pendingReinforcements?.takeIf { it.playerId != playerId },
                setupTroopsToPlaceByPlayer = state.setupTroopsToPlaceByPlayer - playerId,
                tradeRequiredOnNextReinforcementPhaseByPlayer =
                    state.tradeRequiredOnNextReinforcementPhaseByPlayer - playerId,
                usedCheatReinforcementBonusByPlayer =
                    state.usedCheatReinforcementBonusByPlayer - playerId,
                configuredStartPlayerId = updatedTurnState?.startPlayerId,
                turnOrder = updatedTurnOrder,
                turnState =
                    state.turnState?.takeIf { turnState ->
                        updatedPlayers.contains(turnState.activePlayerId) &&
                            updatedPlayers.contains(turnState.startPlayerId)
                    },
                status = updatedStatus,
            )

        return updatedTurnState?.let {
            applyTurnStateUpdate(baseUpdatedState, turnStateUpdatedEvent(baseUpdatedState, it))
        }
            ?: baseUpdatedState.copy(
                activePlayer = null,
                turnNumber = 0,
                turnState = null,
            )
    }

    private fun onPlayerKicked(
        state: GameState,
        targetPlayerId: PlayerId,
        requesterPlayerId: PlayerId,
    ): GameState {
        if (state.lobbyOwner != requesterPlayerId) {
            throw InvalidLobbyEventException(
                "Nur der Lobby Owner kann Spieler kicken. Requester '$requesterPlayerId' " +
                    "ist nicht der Owner '${state.lobbyOwner}'.",
            )
        }

        if (!state.players.contains(targetPlayerId)) {
            throw InvalidLobbyEventException(
                "Player '$targetPlayerId' ist nicht Teil der Lobby '${state.lobbyCode}'.",
            )
        }

        val updatedPlayers = state.players.filterNot { it == targetPlayerId }
        val updatedTurnOrder = state.turnOrder.filterNot { it == targetPlayerId }
        val updatedStatus = preserveLobbyStatus(state, updatedPlayers)
        val updatedTurnState =
            synchronizedTurnStateForLobbySetup(
                state = state,
                turnOrder = updatedTurnOrder,
                previousTurnOrder = state.turnOrder,
                removedPlayerId = targetPlayerId,
            )
        val baseUpdatedState =
            state.copy(
                players = updatedPlayers,
                playerDisplayNames = state.playerDisplayNames - targetPlayerId,
                activePlayer = state.activePlayer?.takeIf(updatedPlayers::contains),
                pendingReinforcements =
                    state.pendingReinforcements?.takeIf { it.playerId != targetPlayerId },
                setupTroopsToPlaceByPlayer = state.setupTroopsToPlaceByPlayer - targetPlayerId,
                tradeRequiredOnNextReinforcementPhaseByPlayer =
                    state.tradeRequiredOnNextReinforcementPhaseByPlayer - targetPlayerId,
                usedCheatReinforcementBonusByPlayer =
                    state.usedCheatReinforcementBonusByPlayer - targetPlayerId,
                configuredStartPlayerId = updatedTurnState?.startPlayerId,
                turnOrder = updatedTurnOrder,
                turnState =
                    state.turnState?.takeIf { turnState ->
                        updatedPlayers.contains(turnState.activePlayerId) &&
                            updatedPlayers.contains(turnState.startPlayerId)
                    },
                status = updatedStatus,
            )

        return updatedTurnState?.let {
            applyTurnStateUpdate(baseUpdatedState, turnStateUpdatedEvent(baseUpdatedState, it))
        }
            ?: baseUpdatedState.copy(
                activePlayer = null,
                turnNumber = 0,
                turnState = null,
            )
    }

    private fun onTurnEnded(
        state: GameState,
        playerId: PlayerId,
    ): GameState {
        if (state.status != GameStatus.RUNNING) {
            throw InvalidLobbyEventException(
                "TurnEnded kann nur im Status RUNNING verarbeitet werden, " +
                    "war aber '${state.status}'.",
            )
        }
        if (state.activePlayer != playerId) {
            throw InvalidLobbyEventException(
                "TurnEnded von '$playerId' ist ungültig, aktiver Spieler ist " +
                    "'${state.activePlayer}'.",
            )
        }
        val turnState =
            state.resolvedTurnState
                ?: throw InvalidLobbyEventException(
                    "TurnEnded kann ohne initialisierten TurnState nicht verarbeitet werden.",
                )
        val updatedTurnState =
            TurnStateMachine.advance(
                turnState = turnState,
                turnOrder = state.turnOrder,
            )

        return applyTurnStateUpdate(
            state = state,
            event = turnStateUpdatedEvent(state, updatedTurnState),
        )
    }

    private fun onTerritoryOwnerChanged(
        state: GameState,
        event: TerritoryOwnerChangedEvent,
    ): GameState {
        requireMapLoaded(state)
        requireKnownTerritory(state, event.territoryId)

        if (event.ownerId != null && !state.hasPlayer(event.ownerId)) {
            throw InvalidLobbyEventException(
                "Territory '${event.territoryId.value}' kann nicht Spieler '${event.ownerId}' " +
                    "zugeordnet werden, da dieser nicht Teil der Lobby '${state.lobbyCode}' ist.",
            )
        }

        val updatedState =
            state.withTerritoryOwner(
                territoryId = event.territoryId,
                ownerId = event.ownerId,
            )

        val winnerPlayerId = updatedState.territoryDominationWinner()
        return if (winnerPlayerId != null) {
            finishMatch(
                state = updatedState,
                reason = MatchEndReason.TERRITORY_DOMINATION,
                winnerPlayerId = winnerPlayerId,
            )
        } else {
            updatedState
        }
    }

    private fun onTerritoryTroopsChanged(
        state: GameState,
        event: TerritoryTroopsChangedEvent,
    ): GameState {
        requireMapLoaded(state)
        requireKnownTerritory(state, event.territoryId)

        return state.withTerritoryTroops(
            territoryId = event.territoryId,
            troopCount = event.troopCount,
        )
    }

    private fun onStartPlayerConfigured(
        state: GameState,
        startPlayerId: PlayerId,
        requesterPlayerId: PlayerId,
    ): GameState {
        if (hasStartedGame(state)) {
            throw InvalidLobbyEventException(
                "Startspieler kann für Lobby '${state.lobbyCode}' nach " +
                    "Spielstart nicht mehr geändert werden.",
            )
        }
        if (state.status == GameStatus.CLOSED || state.status == GameStatus.FINISHED) {
            throw InvalidLobbyEventException(
                "Startspieler kann für Lobby '${state.lobbyCode}' im Status " +
                    "'${state.status}' nicht geändert werden.",
            )
        }
        if (state.lobbyOwner != requesterPlayerId) {
            throw InvalidLobbyEventException(
                "Nur der Lobby Owner kann den Startspieler setzen. " +
                    "Requester '$requesterPlayerId' ist nicht der Owner '${state.lobbyOwner}'.",
            )
        }
        if (!state.hasPlayer(startPlayerId)) {
            throw InvalidLobbyEventException(
                "Startspieler '${startPlayerId.value}' ist nicht Teil der " +
                    "Lobby '${state.lobbyCode}'.",
            )
        }

        val updatedTurnState =
            TurnStateMachine.prepareSetupState(
                turnOrder = state.turnOrder,
                preferredStartPlayerId = startPlayerId,
            ) ?: throw InvalidLobbyEventException(
                "Startspieler kann für Lobby '${state.lobbyCode}' ohne " +
                    "Spieler nicht gesetzt werden.",
            )
        val updatedState =
            state.copy(
                configuredStartPlayerId = updatedTurnState.startPlayerId,
                turnState = null,
            )

        return applyTurnStateUpdate(
            updatedState,
            turnStateUpdatedEvent(updatedState, updatedTurnState),
        )
    }

    private fun onGameStarted(
        state: GameState,
        event: GameStarted,
    ): GameState {
        if (!GameStartPreparation.isSupportedPlayerCount(state.players.size)) {
            throw InvalidLobbyEventException(
                "Spiel kann nur mit 3 bis 6 Spielern gestartet werden, " +
                    "waren aber ${state.players.size}.",
            )
        }

        if (hasStartedGame(state)) {
            throw InvalidLobbyEventException(
                "Spiel für Lobby '${state.lobbyCode}' wurde bereits gestartet.",
            )
        }
        if (state.status == GameStatus.CLOSED || state.status == GameStatus.FINISHED) {
            throw InvalidLobbyEventException(
                "Spiel kann nicht aus Status '${state.status}' gestartet werden.",
            )
        }
        requireMapLoaded(state)

        val preparedStart =
            try {
                GameStartPreparation.prepare(state, event.randomSeed)
            } catch (cause: IllegalArgumentException) {
                throw InvalidLobbyEventException(
                    cause.message ?: "Spielstart konnte nicht vorbereitet werden.",
                )
            }

        val initializedTurnState =
            TurnStateMachine.prepareSetupState(
                turnOrder = preparedStart.randomizedTurnOrder,
                preferredStartPlayerId = state.configuredStartPlayerId,
            )
        val runningState =
            state.copy(
                configuredStartPlayerId = initializedTurnState?.startPlayerId,
                deckState =
                    CardDeckFactory.createShuffledDeck(
                        mapDefinition =
                            state.mapDefinition
                                ?: throw InvalidLobbyEventException(
                                    "Spielstart benötigt eine MapDefinition.",
                                ),
                        randomSeed = event.randomSeed,
                    ),
                discardPileState = DiscardPileState(),
                gameStarted = true,
                gameRandomSeed = event.randomSeed,
                gameRandomState = event.randomSeed,
                pendingReinforcements = null,
                status = GameStatus.RUNNING,
                closedReason = null,
                winnerPlayerId = null,
                territoryCapturedThisTurn = false,
                turnOrder = preparedStart.randomizedTurnOrder,
                territoryStates = preparedStart.preparedTerritoryStates,
                setupTroopsToPlaceByPlayer = preparedStart.setupTroopsToPlaceByPlayer,
            )

        return initializedTurnState?.let {
            applyTurnStateUpdate(runningState, turnStateUpdatedEvent(runningState, it))
        }
            ?: runningState
    }

    private fun onTurnStateUpdated(
        state: GameState,
        event: TurnStateUpdatedEvent,
    ): GameState = applyTurnStateUpdate(state, event)

    private fun onAttackResolved(
        state: GameState,
        event: AttackResolvedEvent,
    ): GameState {
        requireMapLoaded(state)
        requireKnownPlayer(state, event.attackerPlayerId)
        requireKnownTerritory(state, event.fromTerritoryId)
        requireKnownTerritory(state, event.toTerritoryId)

        val currentRngSeed = requireAttackRandomSeed(state)
        val currentRngState = requireAttackRandomState(state)
        validateAttackResolvedPreconditions(state, event, currentRngState)

        val stateAfterLosses =
            state
                .withTerritoryTroops(event.fromTerritoryId, event.attackerRemaining)
                .withTerritoryTroops(event.toTerritoryId, event.defenderRemaining)

        val updatedState = applyAttackOccupationIfNeeded(stateAfterLosses, event)

        check(currentRngSeed == state.gameRandomSeed)
        val stateWithCaptureFlag =
            if (event.capture) {
                updatedState.withTerritoryCapturedThisTurn(true)
            } else {
                updatedState
            }

        return stateWithCaptureFlag.withGameRandomState(event.rngStateAfter)
    }

    private fun requireAttackRandomSeed(state: GameState): Long =
        state.gameRandomSeed
            ?: throw InvalidLobbyEventException(
                "AttackResolvedEvent benötigt initialisierten gameRandomSeed.",
            )

    private fun requireAttackRandomState(state: GameState): Long =
        state.gameRandomState
            ?: throw InvalidLobbyEventException(
                "AttackResolvedEvent benötigt initialisierten gameRandomState.",
            )

    private fun validateAttackResolvedPreconditions(
        state: GameState,
        event: AttackResolvedEvent,
        currentRngState: Long,
    ) {
        if (currentRngState != event.rngStateBefore) {
            throw InvalidLobbyEventException(
                "AttackResolvedEvent.rngStateBefore passt nicht zum aktuellen GameState: " +
                    "erwartet=$currentRngState, war=${event.rngStateBefore}.",
            )
        }
        if (state.ownerOf(event.fromTerritoryId) != event.attackerPlayerId) {
            throw InvalidLobbyEventException(
                "AttackResolvedEvent.fromTerritoryId '${event.fromTerritoryId.value}' gehoert " +
                    "nicht dem Angreifer '${event.attackerPlayerId.value}'.",
            )
        }
        if (state.ownerOf(event.toTerritoryId) != event.defenderPlayerId) {
            throw InvalidLobbyEventException(
                "AttackResolvedEvent.toTerritoryId '${event.toTerritoryId.value}' gehoert " +
                    "nicht dem Verteidiger '${event.defenderPlayerId.value}'.",
            )
        }
        if (state.troopCountOf(event.fromTerritoryId) != event.sourceTroopsBefore) {
            throw InvalidLobbyEventException(
                "AttackResolvedEvent.sourceTroopsBefore passt nicht zum aktuellen GameState.",
            )
        }
        if (state.troopCountOf(event.toTerritoryId) != event.targetTroopsBefore) {
            throw InvalidLobbyEventException(
                "AttackResolvedEvent.targetTroopsBefore passt nicht zum aktuellen GameState.",
            )
        }
    }

    private fun applyAttackOccupationIfNeeded(
        stateAfterLosses: GameState,
        event: AttackResolvedEvent,
    ): GameState =
        if (event.capture) {
            applyCapturedAttackOccupation(stateAfterLosses, event)
        } else {
            validateNonCaptureHasNoOccupationData(event)
            stateAfterLosses
        }

    private fun applyCapturedAttackOccupation(
        stateAfterLosses: GameState,
        event: AttackResolvedEvent,
    ): GameState {
        val occupyingTroopCount = requireOccupyingTroopCount(event)
        val minOccupyingTroops = requireMinOccupyingTroops(event)
        validateCapturedOccupation(event, occupyingTroopCount, minOccupyingTroops)

        return stateAfterLosses
            .withTerritoryTroops(
                event.fromTerritoryId,
                event.attackerRemaining - occupyingTroopCount,
            )
            .withTerritoryOwner(event.toTerritoryId, event.attackerPlayerId)
            .withTerritoryTroops(event.toTerritoryId, occupyingTroopCount)
    }

    private fun requireOccupyingTroopCount(event: AttackResolvedEvent): Int =
        event.occupyingTroopCount
            ?: throw InvalidLobbyEventException(
                "Capture-AttackResolvedEvent benötigt occupyingTroopCount.",
            )

    private fun requireMinOccupyingTroops(event: AttackResolvedEvent): Int =
        event.minOccupyingTroops
            ?: throw InvalidLobbyEventException(
                "Capture-AttackResolvedEvent benötigt minOccupyingTroops.",
            )

    private fun validateCapturedOccupation(
        event: AttackResolvedEvent,
        occupyingTroopCount: Int,
        minOccupyingTroops: Int,
    ) {
        if (occupyingTroopCount < minOccupyingTroops) {
            throw InvalidLobbyEventException(
                "AttackResolvedEvent.occupyingTroopCount unterschreitet " +
                    "minOccupyingTroops: minimum=$minOccupyingTroops, " +
                    "war=$occupyingTroopCount.",
            )
        }
        if (occupyingTroopCount >= event.attackerRemaining) {
            throw InvalidLobbyEventException(
                "AttackResolvedEvent muss mindestens eine Truppe im " +
                    "Ursprungsterritorium lassen.",
            )
        }
    }

    private fun validateNonCaptureHasNoOccupationData(event: AttackResolvedEvent) {
        if (event.occupyingTroopCount != null || event.minOccupyingTroops != null) {
            throw InvalidLobbyEventException(
                "Nicht-eroberter Angriff darf keine Occupation-Daten enthalten.",
            )
        }
    }

    private fun onCheatReinforcementBonusUsed(
        state: GameState,
        event: CheatReinforcementBonusUsedEvent,
    ): GameState {
        /*
         * Der Reducer speichert nur, dass der Bonus verbraucht wurde.
         * Die fachliche Prüfung, ob der Spieler gerade aktiv ist, in der
         * Reinforcements-Phase steht und keinen Pflicht-Kartentausch offen hat,
         * passiert vorher im Server-Routing. Diese Trennung ist wichtig:
         * - Routing entscheidet, ob ein Request erlaubt ist.
         * - Reducer macht aus einem gültigen Event deterministisch neuen State.
         */
        requireKnownPlayer(state, event.playerId)
        if (event.playerId in state.usedCheatReinforcementBonusByPlayer) {
            throw InvalidLobbyEventException(
                "Spieler '${event.playerId.value}' hat den " +
                    "Schummel-Verstärkungsbonus bereits verwendet.",
            )
        }

        return state.copy(
            /*
             * Set statt Boolean pro Spieler: So bleibt die Information klein und
             * lässt sich beim Entfernen eines Spielers einfach wieder aus dem
             * State herausnehmen.
             */
            usedCheatReinforcementBonusByPlayer =
                state.usedCheatReinforcementBonusByPlayer + event.playerId,
        )
    }

    private fun onPendingReinforcementsSet(
        state: GameState,
        event: PendingReinforcementsSetEvent,
    ): GameState {
        requireKnownPlayer(state, event.playerId)
        return state.withPendingReinforcements(event.playerId, event.amount)
    }

    private fun onPlayerEliminated(
        state: GameState,
        event: PlayerEliminatedEvent,
    ): GameState {
        requireKnownPlayer(state, event.playerId)
        requireKnownPlayer(state, event.eliminatedByPlayerId)
        require(hasStartedGame(state)) {
            "PlayerEliminatedEvent darf nur in einem gestarteten Spiel angewendet werden."
        }
        require(state.turnOrder.contains(event.playerId)) {
            "PlayerEliminatedEvent.playerId '${event.playerId.value}' ist bereits nicht mehr " +
                "Teil der aktiven TurnOrder."
        }
        require(state.ownedTerritoryCount(event.playerId) == 0) {
            "PlayerEliminatedEvent.playerId '${event.playerId.value}' besitzt noch Territorien."
        }

        val updatedTurnOrder = state.turnOrder.filterNot { it == event.playerId }
        val stateWithTransferredCards =
            state.withAllCardsTransferred(
                fromPlayerId = event.playerId,
                toPlayerId = event.eliminatedByPlayerId,
            )
        val attackerHandAfterTransfer = stateWithTransferredCards.handOf(event.eliminatedByPlayerId)
        val attackerNeedsTradeOnNextReinforcementPhase =
            attackerHandAfterTransfer.size > 4 &&
                CardSetValidator.canMakeAnySet(attackerHandAfterTransfer)
        val updatedTurnState =
            synchronizedTurnStateForLobbySetup(
                state = state,
                turnOrder = updatedTurnOrder,
                previousTurnOrder = state.turnOrder,
                removedPlayerId = event.playerId,
            )
        val updatedStatus =
            preserveLobbyStatus(
                state = state,
                updatedPlayers = state.players,
                updatedActivePlayers = updatedTurnOrder,
            )
        val baseUpdatedState =
            stateWithTransferredCards
                .withTradeRequiredOnNextReinforcementPhase(event.playerId, false)
                .withTradeRequiredOnNextReinforcementPhase(
                    event.eliminatedByPlayerId,
                    attackerNeedsTradeOnNextReinforcementPhase,
                ).copy(
                    activePlayer = state.activePlayer?.takeIf(updatedTurnOrder::contains),
                    pendingReinforcements =
                        state.pendingReinforcements?.takeIf { it.playerId != event.playerId },
                    configuredStartPlayerId = updatedTurnState?.startPlayerId,
                    turnOrder = updatedTurnOrder,
                    turnState =
                        state.turnState?.takeIf { turnState ->
                            updatedTurnOrder.contains(turnState.activePlayerId) &&
                                updatedTurnOrder.contains(turnState.startPlayerId)
                        },
                    status = updatedStatus,
                )

        return updatedTurnState?.let {
            applyTurnStateUpdate(baseUpdatedState, turnStateUpdatedEvent(baseUpdatedState, it))
        }
            ?: baseUpdatedState.copy(
                activePlayer = null,
                turnNumber = 0,
                turnState = null,
            )
    }

    private fun onPendingReinforcementsChanged(
        state: GameState,
        event: PendingReinforcementsChangedEvent,
    ): GameState {
        requireKnownPlayer(state, event.playerId)
        val currentAmount = pendingReinforcementsAmountFor(state, event.playerId)
        val exactSum = currentAmount.toLong() + event.delta.toLong()
        if (exactSum !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            throw InvalidLobbyEventException(
                "PendingReinforcements für Spieler '${event.playerId.value}' dürfen den " +
                    "Int-Bereich nicht verlassen: aktuell=$currentAmount, " +
                    "delta=${event.delta}.",
            )
        }
        val updatedAmount = exactSum.toInt()
        if (updatedAmount < 0) {
            throw InvalidLobbyEventException(
                "PendingReinforcements für Spieler '${event.playerId.value}' dürfen nicht " +
                    "negativ werden: aktuell=$currentAmount, delta=${event.delta}.",
            )
        }
        return state.withPendingReinforcements(event.playerId, updatedAmount)
    }

    private fun onCardSetTradedIn(
        state: GameState,
        event: CardSetTradedInEvent,
    ): GameState {
        requireKnownPlayer(state, event.playerId)

        val expectedTradeIndex = state.tradedInSetCount + 1
        if (event.tradeIndex != expectedTradeIndex) {
            throw InvalidLobbyEventException(
                "CardSetTradedInEvent.tradeIndex muss den naechsten globalen " +
                    "Trade-In abbilden: erwartet=$expectedTradeIndex, war=${event.tradeIndex}.",
            )
        }

        val expectedValue = TradeInProgression.tradeInValue(event.tradeIndex)
        if (event.value != expectedValue) {
            throw InvalidLobbyEventException(
                "CardSetTradedInEvent.value passt nicht zur Progression: " +
                    "erwartet=$expectedValue, war=${event.value}.",
            )
        }

        return state
            .withTradedInSetCount(event.tradeIndex)
            .withTradeRequiredOnNextReinforcementPhase(event.playerId, false)
    }

    private fun onCardDrawn(
        state: GameState,
        event: CardDrawnEvent,
    ): GameState {
        requireKnownPlayer(state, event.playerId)

        val currentTurnState =
            state.resolvedTurnState
                ?: throw InvalidLobbyEventException(
                    "CardDrawnEvent benötigt einen initialisierten TurnState.",
                )
        if (currentTurnState.activePlayerId != event.playerId) {
            throw InvalidLobbyEventException(
                "CardDrawnEvent.playerId '${event.playerId.value}' ist nicht aktiver Spieler.",
            )
        }
        if (currentTurnState.turnPhase != TurnPhase.DRAW_CARD) {
            throw InvalidLobbyEventException(
                "CardDrawnEvent ist nur in Phase DRAW_CARD erlaubt.",
            )
        }
        if (!state.territoryCapturedThisTurn) {
            throw InvalidLobbyEventException(
                "CardDrawnEvent benötigt mindestens eine Eroberung im aktuellen Zug.",
            )
        }

        return state
            .withCardDrawnFromDeck(event.playerId, event.cardId)
            .withTerritoryCapturedThisTurn(false)
    }

    private fun onMatchEnded(
        state: GameState,
        event: MatchEndedEvent,
    ): GameState {
        val mayEnrichFinishedState =
            state.status == GameStatus.FINISHED &&
                state.closedReason == null &&
                state.winnerPlayerId == null
        if (state.status != GameStatus.RUNNING && !mayEnrichFinishedState) {
            throw InvalidLobbyEventException(
                "MatchEndedEvent kann nur im Status RUNNING verarbeitet werden, " +
                    "war aber '${state.status}'.",
            )
        }
        if (event.winnerPlayerId != null) {
            requireKnownPlayer(state, event.winnerPlayerId)
        }

        return finishMatch(
            state = state,
            reason = event.reason,
            winnerPlayerId = event.winnerPlayerId,
        )
    }

    private fun finishMatch(
        state: GameState,
        reason: MatchEndReason,
        winnerPlayerId: PlayerId?,
    ): GameState =
        state.copy(
            status = GameStatus.FINISHED,
            closedReason = reason.name,
            winnerPlayerId = winnerPlayerId,
            pendingReinforcements = null,
            territoryCapturedThisTurn = false,
        )

    private fun onPlayerCardsRemoved(
        state: GameState,
        event: PlayerCardsRemovedEvent,
    ): GameState {
        requireKnownPlayer(state, event.playerId)

        return state.withCardsMovedFromHandToDiscard(event.playerId, event.cardIds)
    }

    private fun onFortifyMoveApplied(
        state: GameState,
        event: FortifyMoveAppliedEvent,
    ): GameState {
        requireMapLoaded(state)
        requireKnownTerritory(state, event.fromTerritoryId)
        requireKnownTerritory(state, event.toTerritoryId)

        if (
            state.ownerOf(event.fromTerritoryId) != event.playerId ||
            state.ownerOf(event.toTerritoryId) != event.playerId
        ) {
            throw InvalidLobbyEventException(
                "FortifyMoveAppliedEvent benötigt zwei Territorien von Spieler " +
                    "'${event.playerId.value}'.",
            )
        }

        val sourceTroops = state.troopCountOf(event.fromTerritoryId)
        if (sourceTroops <= event.troopCount) {
            throw InvalidLobbyEventException(
                "FortifyMoveAppliedEvent von '${event.fromTerritoryId.value}' nach " +
                    "'${event.toTerritoryId.value}' muss mindestens eine Truppe " +
                    "zurücklassen: vorhanden=$sourceTroops, bewegt=${event.troopCount}.",
            )
        }

        return state
            .withTerritoryTroops(
                territoryId = event.fromTerritoryId,
                troopCount = sourceTroops - event.troopCount,
            ).withTerritoryTroops(
                territoryId = event.toTerritoryId,
                troopCount = state.troopCountOf(event.toTerritoryId) + event.troopCount,
            )
    }

    private fun applyTurnStateUpdate(
        state: GameState,
        event: TurnStateUpdatedEvent,
    ): GameState {
        require(state.hasPlayer(event.activePlayerId)) {
            throw InvalidLobbyEventException(
                "TurnStateUpdatedEvent.activePlayerId " +
                    "'${event.activePlayerId.value}' ist nicht Teil der Lobby " +
                    "'${state.lobbyCode}'.",
            )
        }
        require(state.hasPlayer(event.startPlayerId)) {
            throw InvalidLobbyEventException(
                "TurnStateUpdatedEvent.startPlayerId " +
                    "'${event.startPlayerId.value}' ist nicht Teil der Lobby " +
                    "'${state.lobbyCode}'.",
            )
        }
        require(state.turnOrder.contains(event.activePlayerId)) {
            throw InvalidLobbyEventException(
                "TurnStateUpdatedEvent.activePlayerId " +
                    "'${event.activePlayerId.value}' ist nicht Teil der TurnOrder " +
                    "von Lobby '${state.lobbyCode}'.",
            )
        }
        require(state.turnOrder.contains(event.startPlayerId)) {
            throw InvalidLobbyEventException(
                "TurnStateUpdatedEvent.startPlayerId " +
                    "'${event.startPlayerId.value}' ist nicht Teil der TurnOrder " +
                    "von Lobby '${state.lobbyCode}'.",
            )
        }
        if (event.pausedPlayerId != null && !state.hasPlayer(event.pausedPlayerId)) {
            throw InvalidLobbyEventException(
                "TurnStateUpdatedEvent.pausedPlayerId " +
                    "'${event.pausedPlayerId.value}' ist nicht Teil der Lobby " +
                    "'${state.lobbyCode}'.",
            )
        }
        if (
            event.pauseReason == TurnPauseReasons.WAITING_FOR_PLAYER &&
            event.pausedPlayerId != event.activePlayerId
        ) {
            throw InvalidLobbyEventException(
                "TurnStateUpdatedEvent.pausedPlayerId muss bei " +
                    "WAITING_FOR_PLAYER dem aktiven Spieler entsprechen.",
            )
        }

        val currentTurnCount = state.resolvedTurnState?.turnCount ?: 0
        if (event.turnCount < currentTurnCount) {
            throw InvalidLobbyEventException(
                "TurnStateUpdatedEvent.turnCount darf nicht rückwärts " +
                    "laufen: aktuell=$currentTurnCount, neu=${event.turnCount}.",
            )
        }

        val updatedTurnState =
            TurnState(
                activePlayerId = event.activePlayerId,
                turnPhase = event.turnPhase,
                turnCount = event.turnCount,
                startPlayerId = event.startPlayerId,
                isPaused = event.isPaused,
                pauseReason = event.pauseReason,
                pausedPlayerId = event.pausedPlayerId,
            )
        val shouldResetTurnScopedFlags =
            state.activePlayer != updatedTurnState.activePlayerId ||
                state.turnNumber != updatedTurnState.turnCount

        return state.copy(
            activePlayer = updatedTurnState.activePlayerId,
            configuredStartPlayerId = updatedTurnState.startPlayerId,
            turnNumber = updatedTurnState.turnCount,
            turnState = updatedTurnState,
            fortifyUsedThisTurn = state.fortifyUsedThisTurn && !shouldResetTurnScopedFlags,
            territoryCapturedThisTurn =
                state.territoryCapturedThisTurn && !shouldResetTurnScopedFlags,
        )
    }

    private fun turnStateUpdatedEvent(
        state: GameState,
        turnState: TurnState,
    ): TurnStateUpdatedEvent =
        TurnStateUpdatedEvent(
            lobbyCode = state.lobbyCode,
            activePlayerId = turnState.activePlayerId,
            turnPhase = turnState.turnPhase,
            turnCount = turnState.turnCount,
            startPlayerId = turnState.startPlayerId,
            isPaused = turnState.isPaused,
            pauseReason = turnState.pauseReason,
            pausedPlayerId = turnState.pausedPlayerId,
        )

    private fun preserveLobbyStatus(
        state: GameState,
        updatedPlayers: List<PlayerId>,
        updatedActivePlayers: List<PlayerId> = updatedPlayers,
    ): GameStatus =
        when {
            state.status == GameStatus.CLOSED -> GameStatus.CLOSED
            state.status == GameStatus.FINISHED -> GameStatus.FINISHED
            hasStartedGame(state) && updatedActivePlayers.size <= 1 -> GameStatus.FINISHED
            hasStartedGame(state) && updatedActivePlayers.size >= 2 -> GameStatus.RUNNING
            else -> GameStatus.WAITING_FOR_PLAYERS
        }

    private fun synchronizedTurnStateForLobbySetup(
        state: GameState,
        turnOrder: List<PlayerId>,
        previousTurnOrder: List<PlayerId>,
        removedPlayerId: PlayerId? = null,
    ): TurnState? =
        if (hasStartedGame(state)) {
            TurnStateMachine.synchronizeWithPlayers(
                turnState = state.resolvedTurnState,
                turnOrder = turnOrder,
                previousTurnOrder = previousTurnOrder,
                removedPlayerId = removedPlayerId,
            )
        } else {
            val preferredStartPlayerId =
                state.configuredStartPlayerId
                    ?.takeIf(turnOrder::contains)
                    ?: turnOrder.firstOrNull()
            TurnStateMachine.prepareSetupState(
                turnOrder = turnOrder,
                preferredStartPlayerId = preferredStartPlayerId,
            )
        }

    private fun hasStartedGame(state: GameState): Boolean =
        state.gameStarted || state.status == GameStatus.RUNNING

    private fun requireMapLoaded(state: GameState) {
        if (!state.hasMap()) {
            throw InvalidLobbyEventException(
                "Map-State ist für Lobby '${state.lobbyCode}' noch nicht initialisiert.",
            )
        }
    }

    private fun requireKnownPlayer(
        state: GameState,
        playerId: PlayerId,
    ) {
        if (!state.hasPlayer(playerId)) {
            throw InvalidLobbyEventException(
                "Spieler '${playerId.value}' ist nicht Teil der Lobby '${state.lobbyCode.value}'.",
            )
        }
    }

    private fun requireKnownTerritory(
        state: GameState,
        territoryId: at.aau.pulverfass.shared.ids.TerritoryId,
    ) {
        if (state.territoryStateOf(territoryId) == null) {
            throw InvalidLobbyEventException(
                "Territory '${territoryId.value}' ist nicht Teil der Map " +
                    "von Lobby '${state.lobbyCode}'.",
            )
        }
    }

    private fun pendingReinforcementsAmountFor(
        state: GameState,
        playerId: PlayerId,
    ): Int {
        val current = state.pendingReinforcements ?: return 0
        if (current.playerId != playerId) {
            throw InvalidLobbyEventException(
                "PendingReinforcements gehören Spieler '${current.playerId.value}' und " +
                    "können nicht für '${playerId.value}' verändert werden.",
            )
        }
        return current.amount
    }
}
