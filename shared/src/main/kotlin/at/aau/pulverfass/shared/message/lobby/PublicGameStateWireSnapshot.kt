package at.aau.pulverfass.shared.message.lobby

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import kotlinx.serialization.Serializable

/**
 * Gemeinsame Wire-Repräsentation der oeffentlichen Full-Snapshot-Felder.
 *
 * Catch-up-Antworten und Snapshot-Broadcasts transportieren dieselben Daten in
 * unterschiedlichem fachlichen Kontext. Diese interne DTO vermeidet doppelte
 * manuelle Feld-Serialisierung.
 */
@Serializable
internal data class PublicGameStateWireSnapshot(
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val determinism: PublicDeterminismMetadataSnapshot,
    val turnState: PublicTurnStateSnapshot,
    val definition: MapDefinitionSnapshot,
    val territoryStates: List<MapTerritoryStateSnapshot>,
    val gameStatus: GameStatus = GameStatus.RUNNING,
    val matchEndReason: String? = null,
    val winnerPlayerId: PlayerId? = null,
)
