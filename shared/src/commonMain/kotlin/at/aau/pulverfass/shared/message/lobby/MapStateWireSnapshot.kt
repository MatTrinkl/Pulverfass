package at.aau.pulverfass.shared.message.lobby

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import kotlinx.serialization.Serializable

/**
 * Gemeinsame Wire-Repräsentation der öffentlichen Map-Snapshot-Felder.
 *
 * Sie reduziert doppelte manuelle Serialisierung an Transportgrenzen, an denen
 * dieselbe Datenstruktur fachlich als eigener Nachrichtentyp verwendet wird.
 */
@Serializable
internal data class MapStateWireSnapshot(
    val lobbyCode: LobbyCode,
    val schemaVersion: Int,
    val mapHash: String,
    val stateVersion: Long,
    val definition: MapDefinitionSnapshot,
    val territoryStates: List<MapTerritoryStateSnapshot>,
)
