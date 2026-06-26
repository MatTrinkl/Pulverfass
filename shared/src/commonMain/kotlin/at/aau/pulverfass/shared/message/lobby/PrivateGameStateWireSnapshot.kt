package at.aau.pulverfass.shared.message.lobby

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import kotlinx.serialization.Serializable

/**
 * Gemeinsame Wire-Repräsentation der privaten Snapshot-Felder eines Spielers.
 */
@Serializable
internal data class PrivateGameStateWireSnapshot(
    val lobbyCode: LobbyCode,
    val recipientPlayerId: PlayerId,
    val stateVersion: Long,
    val handCards: List<String>,
    val secretObjectives: List<String>,
    val privateHandCards: List<PrivateHandCardSnapshot> = emptyList(),
)
