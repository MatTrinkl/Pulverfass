package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Atomare Aktualisierung des kombinierten Turn-Zustands einer Lobby.
 *
 * Dieses Event ist die einzige fachliche Mutationsquelle für den TurnState im
 * GameState. Änderungen an aktivem Spieler, Phase, Rundenzähler oder
 * Pause-Status werden bewusst nicht in einzelne Teil-Events aufgespalten.
 *
 * @property lobbyCode betroffene Lobby
 * @property activePlayerId aktuell aktiver Spieler
 * @property turnPhase aktuell aktive Phase
 * @property turnCount Rundenzähler ab der ersten gestarteten Runde
 * @property startPlayerId Referenzspieler für Rundenerkennung
 * @property isPaused signalisiert einen pausierten Turn-State
 * @property pauseReason optionale Begründung für den Pause-Zustand
 * @property pausedPlayerId optionaler Spieler, auf dessen Verbindung gewartet wird
 */
@Serializable
data class TurnStateUpdatedEvent(
    override val lobbyCode: LobbyCode,
    val activePlayerId: PlayerId,
    val turnPhase: TurnPhase,
    val turnCount: Int,
    val startPlayerId: PlayerId,
    @EncodeDefault
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val pausedPlayerId: PlayerId? = null,
) : InternalLobbyEvent, PublicGameEvent {
    init {
        require(turnCount >= 1) {
            "TurnStateUpdatedEvent.turnCount darf nicht kleiner als 1 sein, war aber $turnCount."
        }
        require(!isPaused || !pauseReason.isNullOrBlank()) {
            "TurnStateUpdatedEvent.pauseReason muss gesetzt sein, wenn isPaused=true ist."
        }
        require(isPaused || pauseReason == null) {
            "TurnStateUpdatedEvent.pauseReason darf nur gesetzt sein, wenn isPaused=true ist."
        }
        require(isPaused || pausedPlayerId == null) {
            "TurnStateUpdatedEvent.pausedPlayerId darf nur gesetzt sein, wenn isPaused=true ist."
        }
        require(pausedPlayerId == null || pauseReason == TurnPauseReasons.WAITING_FOR_PLAYER) {
            "TurnStateUpdatedEvent.pausedPlayerId darf nur mit PauseReason " +
                "'${TurnPauseReasons.WAITING_FOR_PLAYER}' gesetzt sein."
        }
        require(pauseReason != TurnPauseReasons.WAITING_FOR_PLAYER || pausedPlayerId != null) {
            "TurnStateUpdatedEvent.pausedPlayerId muss gesetzt sein, wenn " +
                "pauseReason='${TurnPauseReasons.WAITING_FOR_PLAYER}' ist."
        }
        require(pausedPlayerId == null || pausedPlayerId == activePlayerId) {
            "TurnStateUpdatedEvent.pausedPlayerId muss dem aktiven Spieler entsprechen."
        }
    }
}

/**
 * Technischer Serializer für [TurnStateUpdatedEvent].
 */
object TurnStateUpdatedEventSerializer :
    KSerializer<TurnStateUpdatedEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TurnStateUpdatedEvent.serializer(),
    )
