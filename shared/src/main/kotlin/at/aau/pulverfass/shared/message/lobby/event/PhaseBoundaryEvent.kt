package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Expliziter Marker für den Abschluss einer Turn-Phase.
 *
 * Das Event signalisiert Clients, dass die Verarbeitung der vorherigen Phase
 * abgeschlossen ist und die nächste Phase ab `stateVersion` gilt.
 *
 * @property lobbyCode betroffene Lobby
 * @property stateVersion autoritative Version nach dem Phasenwechsel
 * @property previousPhase abgeschlossene Phase
 * @property nextPhase neue aktive Phase
 * @property activePlayerId aktiver Spieler in der neuen Phase
 * @property turnCount Rundenzähler der neuen Phase
 */
@Serializable
data class PhaseBoundaryEvent(
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val previousPhase: TurnPhase,
    val nextPhase: TurnPhase,
    val activePlayerId: PlayerId,
    val turnCount: Int,
) : PublicGameStatePayload {
    init {
        require(stateVersion >= 1) {
            "PhaseBoundaryEvent.stateVersion darf nicht kleiner als 1 sein, war aber $stateVersion."
        }
        require(turnCount >= 1) {
            "PhaseBoundaryEvent.turnCount darf nicht kleiner als 1 sein, war aber $turnCount."
        }
        require(previousPhase != nextPhase) {
            "PhaseBoundaryEvent.previousPhase und nextPhase muessen verschieden sein."
        }
    }
}

/**
 * Technischer Serializer für [PhaseBoundaryEvent].
 */
object PhaseBoundaryEventSerializer :
    KSerializer<PhaseBoundaryEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PhaseBoundaryEvent.serializer(),
    )
