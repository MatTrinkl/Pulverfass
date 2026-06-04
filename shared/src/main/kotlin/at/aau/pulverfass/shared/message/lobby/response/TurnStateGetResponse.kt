package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.PublicGameStatePayload
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort des Servers mit dem aktuellen autoritativen Turn-State.
 *
 * @property lobbyCode betroffene Lobby
 * @property activePlayerId aktuell aktiver Spieler
 * @property turnPhase aktuell aktive Phase
 * @property turnCount Rundenzähler ab Startspieler
 * @property startPlayerId Referenzspieler für den Rundenwechsel
 * @property isPaused signalisiert pausierten Turn-State
 * @property pauseReason optionale Begründung für Pause
 * @property pausedPlayerId optionaler Spieler, auf dessen Verbindung gewartet wird
 */
@Serializable
data class TurnStateGetResponse(
    val lobbyCode: LobbyCode,
    val activePlayerId: PlayerId,
    val turnPhase: TurnPhase,
    val turnCount: Int,
    val startPlayerId: PlayerId,
    @EncodeDefault
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val pausedPlayerId: PlayerId? = null,
) : PublicGameStatePayload {
    companion object {
        fun fromGameState(gameState: GameState): TurnStateGetResponse {
            val resolvedTurnState =
                gameState.resolvedTurnState
                    ?: throw IllegalStateException(
                        "GameState enthält keinen TurnState für einen Snapshot.",
                    )

            return TurnStateGetResponse(
                lobbyCode = gameState.lobbyCode,
                activePlayerId = resolvedTurnState.activePlayerId,
                turnPhase = resolvedTurnState.turnPhase,
                turnCount = resolvedTurnState.turnCount,
                startPlayerId = resolvedTurnState.startPlayerId,
                isPaused = resolvedTurnState.isPaused,
                pauseReason = resolvedTurnState.pauseReason,
                pausedPlayerId = resolvedTurnState.pausedPlayerId,
            )
        }
    }
}

/**
 * Technischer Serializer für [TurnStateGetResponse].
 */
object TurnStateGetResponseSerializer :
    KSerializer<TurnStateGetResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TurnStateGetResponse.serializer(),
    )
