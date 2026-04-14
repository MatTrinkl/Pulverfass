package at.aau.pulverfass.shared.lobby.reducer

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyClosed
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.PlayerLeft
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.event.TimeoutTriggered
import at.aau.pulverfass.shared.lobby.event.TurnEnded
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus

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
                        closedReason = event.reason,
                    )

                is LobbyCreated ->
                    state.copy(
                        status = GameStatus.WAITING_FOR_PLAYERS,
                        closedReason = null,
                    )

                is PlayerJoined -> onPlayerJoined(state, event.playerId)
                is PlayerLeft -> onPlayerLeft(state, event.playerId)
                is SystemTick -> state
                is TimeoutTriggered -> state
                is TurnEnded -> onTurnEnded(state, event.playerId)
            }

        return updatedState.withMetadata(context)
    }

    private fun onPlayerJoined(
        state: GameState,
        playerId: PlayerId,
    ): GameState {
        if (state.players.contains(playerId)) {
            throw InvalidLobbyEventException(
                "Player '$playerId' ist bereits Teil der Lobby '${state.lobbyCode}'.",
            )
        }

        val updatedPlayers = state.players + playerId
        val updatedTurnOrder = state.turnOrder + playerId
        val updatedStatus =
            when {
                state.status == GameStatus.CLOSED -> GameStatus.CLOSED
                state.status == GameStatus.FINISHED -> GameStatus.FINISHED
                updatedPlayers.size >= 2 -> GameStatus.RUNNING
                else -> GameStatus.WAITING_FOR_PLAYERS
            }

        return state.copy(
            players = updatedPlayers,
            activePlayer = state.activePlayer ?: playerId,
            turnOrder = updatedTurnOrder,
            status = updatedStatus,
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
        val updatedActivePlayer =
            when {
                updatedPlayers.isEmpty() -> null
                state.activePlayer != playerId -> state.activePlayer
                else -> nextPlayerAfter(playerId, updatedTurnOrder)
            }
        val updatedStatus =
            when {
                state.status == GameStatus.CLOSED -> GameStatus.CLOSED
                state.status == GameStatus.FINISHED -> GameStatus.FINISHED
                updatedPlayers.size >= 2 -> GameStatus.RUNNING
                else -> GameStatus.WAITING_FOR_PLAYERS
            }

        return state.copy(
            players = updatedPlayers,
            activePlayer = updatedActivePlayer,
            turnOrder = updatedTurnOrder,
            status = updatedStatus,
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
        return state.copy(
            activePlayer = nextPlayerAfter(playerId, state.turnOrder),
            turnNumber = state.turnNumber + 1,
        )
    }

    private fun nextPlayerAfter(
        playerId: PlayerId,
        order: List<PlayerId>,
    ): PlayerId? {
        val currentIndex = order.indexOf(playerId)
        if (currentIndex == -1) {
            return order.firstOrNull()
        }

        return order[(currentIndex + 1) % order.size]
    }
}
