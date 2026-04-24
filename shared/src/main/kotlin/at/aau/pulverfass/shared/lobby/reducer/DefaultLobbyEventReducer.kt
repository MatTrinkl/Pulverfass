package at.aau.pulverfass.shared.lobby.reducer

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
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
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnOrderPolicy
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
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
                        turnState = null,
                        closedReason = event.reason,
                    )

                is LobbyCreated ->
                    state.copy(
                        status = GameStatus.WAITING_FOR_PLAYERS,
                        closedReason = null,
                    )

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
                is GameStarted -> onGameStarted(state)
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
        val baseUpdatedState =
            state.copy(
                players = updatedPlayers,
                playerDisplayNames = state.playerDisplayNames + (playerId to playerDisplayName),
                lobbyOwner = updatedLobbyOwner,
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

        return state.withTerritoryOwner(
            territoryId = event.territoryId,
            ownerId = event.ownerId,
        )
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

    private fun onGameStarted(state: GameState): GameState {
        if (state.players.size < 2) {
            throw InvalidLobbyEventException(
                "Spiel kann nicht mit weniger als 2 Spielern gestartet werden, " +
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

        val initializedTurnState =
            TurnStateMachine.prepareSetupState(
                turnOrder = state.turnOrder,
                preferredStartPlayerId = state.configuredStartPlayerId,
            )
        val runningState =
            state.copy(
                configuredStartPlayerId = initializedTurnState?.startPlayerId,
                gameStarted = true,
                status = GameStatus.RUNNING,
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

        return state.copy(
            activePlayer = updatedTurnState.activePlayerId,
            configuredStartPlayerId = updatedTurnState.startPlayerId,
            turnNumber = updatedTurnState.turnCount,
            turnState = updatedTurnState,
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
    ): GameStatus =
        when {
            state.status == GameStatus.CLOSED -> GameStatus.CLOSED
            state.status == GameStatus.FINISHED -> GameStatus.FINISHED
            hasStartedGame(state) && updatedPlayers.size >= 2 -> GameStatus.RUNNING
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
}
