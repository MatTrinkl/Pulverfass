package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Kapselt die deterministische Turn- und Spielerreihenfolge für eine Lobby.
 */
object TurnStateMachine {
    fun initialize(turnOrder: List<PlayerId>): TurnState? {
        return prepareSetupState(turnOrder)
    }

    fun prepareSetupState(
        turnOrder: List<PlayerId>,
        preferredStartPlayerId: PlayerId? = null,
    ): TurnState? {
        val normalizedOrder = TurnOrderPolicy.normalize(turnOrder)
        val startPlayer =
            preferredStartPlayerId?.takeIf(normalizedOrder::contains)
                ?: normalizedOrder.firstOrNull()
                ?: return null

        return TurnState(
            activePlayerId = startPlayer,
            turnPhase = TurnPhase.REINFORCEMENTS,
            turnCount = 1,
            startPlayerId = startPlayer,
        )
    }

    fun fromLegacy(
        activePlayer: PlayerId?,
        turnOrder: List<PlayerId>,
        turnNumber: Int,
    ): TurnState? {
        val normalizedOrder = TurnOrderPolicy.normalize(turnOrder)
        val resolvedActivePlayer = activePlayer ?: normalizedOrder.firstOrNull() ?: return null
        val resolvedStartPlayer = normalizedOrder.firstOrNull() ?: resolvedActivePlayer

        return TurnState(
            activePlayerId = resolvedActivePlayer,
            turnPhase = TurnPhase.REINFORCEMENTS,
            turnCount = maxOf(1, turnNumber),
            startPlayerId = resolvedStartPlayer,
        )
    }

    fun advance(
        turnState: TurnState,
        turnOrder: List<PlayerId>,
    ): TurnState {
        require(!turnState.isPaused) {
            "TurnState ist pausiert und kann nicht fortgeschaltet werden."
        }

        val normalizedOrder = TurnOrderPolicy.normalize(turnOrder)
        require(normalizedOrder.contains(turnState.activePlayerId)) {
            "Aktiver Spieler '${turnState.activePlayerId}' ist nicht Teil der TurnOrder."
        }
        require(normalizedOrder.contains(turnState.startPlayerId)) {
            "Startspieler '${turnState.startPlayerId}' ist nicht Teil der TurnOrder."
        }

        if (turnState.turnPhase != TurnPhase.DRAW_CARD) {
            return turnState.copy(turnPhase = turnState.turnPhase.next())
        }

        val nextPlayer =
            TurnOrderPolicy.nextAfter(turnState.activePlayerId, normalizedOrder)
                ?: turnState.activePlayerId
        val switchedPlayer = nextPlayer != turnState.activePlayerId
        val nextTurnCount =
            if (switchedPlayer && nextPlayer == turnState.startPlayerId) {
                turnState.turnCount + 1
            } else {
                turnState.turnCount
            }

        return turnState.copy(
            activePlayerId = nextPlayer,
            turnPhase = TurnPhase.REINFORCEMENTS,
            turnCount = nextTurnCount,
        )
    }

    fun synchronizeWithPlayers(
        turnState: TurnState?,
        turnOrder: List<PlayerId>,
        previousTurnOrder: List<PlayerId> = turnOrder,
        removedPlayerId: PlayerId? = null,
    ): TurnState? {
        val normalizedOrder = TurnOrderPolicy.normalize(turnOrder)
        val normalizedPreviousOrder = TurnOrderPolicy.normalize(previousTurnOrder)
        if (normalizedOrder.isEmpty()) {
            return null
        }

        val current = turnState ?: return initialize(normalizedOrder)
        val activePlayerId =
            when {
                normalizedOrder.contains(current.activePlayerId) -> current.activePlayerId
                removedPlayerId != null ->
                    TurnOrderPolicy.nextAfterRemoved(
                        removedPlayerId = removedPlayerId,
                        previousTurnOrder = normalizedPreviousOrder,
                        currentTurnOrder = normalizedOrder,
                    )
                else -> normalizedOrder.first()
            } ?: normalizedOrder.first()
        val startPlayerId =
            if (normalizedOrder.contains(current.startPlayerId)) {
                current.startPlayerId
            } else {
                normalizedOrder.first()
            }
        val turnPhase =
            if (normalizedOrder.contains(current.activePlayerId)) {
                current.turnPhase
            } else {
                TurnPhase.REINFORCEMENTS
            }
        val pausedPlayerId = current.pausedPlayerId?.takeIf(normalizedOrder::contains)
        val pauseReason =
            when {
                !current.isPaused -> null
                current.pauseReason == TurnPauseReasons.WAITING_FOR_PLAYER &&
                    pausedPlayerId == null -> null
                else -> current.pauseReason
            }
        val isPaused = pauseReason != null

        return current.copy(
            activePlayerId = activePlayerId,
            turnPhase = turnPhase,
            startPlayerId = startPlayerId,
            isPaused = isPaused,
            pauseReason = pauseReason,
            pausedPlayerId = if (isPaused) pausedPlayerId else null,
        )
    }
}

internal object TurnOrderPolicy {
    fun normalize(players: List<PlayerId>): List<PlayerId> = players.distinct()

    fun nextAfter(
        currentPlayerId: PlayerId,
        turnOrder: List<PlayerId>,
    ): PlayerId? {
        if (turnOrder.isEmpty()) {
            return null
        }

        val currentIndex = turnOrder.indexOf(currentPlayerId)
        if (currentIndex == -1) {
            return turnOrder.firstOrNull()
        }

        return (turnOrder.drop(currentIndex + 1) + turnOrder.take(currentIndex + 1)).firstOrNull()
    }

    fun nextAfterRemoved(
        removedPlayerId: PlayerId,
        previousTurnOrder: List<PlayerId>,
        currentTurnOrder: List<PlayerId>,
    ): PlayerId? {
        if (currentTurnOrder.isEmpty()) {
            return null
        }

        val removedIndex = previousTurnOrder.indexOf(removedPlayerId)
        if (removedIndex == -1) {
            return currentTurnOrder.firstOrNull()
        }

        val candidates =
            previousTurnOrder.drop(removedIndex + 1) + previousTurnOrder.take(removedIndex)

        return candidates.firstOrNull { candidate -> currentTurnOrder.contains(candidate) }
            ?: currentTurnOrder.firstOrNull()
    }
}
