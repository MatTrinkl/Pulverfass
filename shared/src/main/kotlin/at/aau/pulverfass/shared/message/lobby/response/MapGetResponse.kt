package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.MapStateWireSnapshot
import at.aau.pulverfass.shared.message.lobby.event.PublicGameStatePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort des Servers mit vollständigem Map-Snapshot.
 *
 * @property lobbyCode betroffene Lobby
 * @property schemaVersion Version des Map-Schemas
 * @property mapHash stabiler Hash der Map-Definition
 * @property stateVersion aktuelle Version des autoritativen GameStates
 * @property definition readonly Teil der Map
 * @property territoryStates mutierbarer Zustand aller Territorien
 */
@Serializable(with = MapGetResponseSerializer::class)
data class MapGetResponse(
    val lobbyCode: LobbyCode,
    val schemaVersion: Int,
    val mapHash: String,
    val stateVersion: Long,
    val definition: MapDefinitionSnapshot,
    val territoryStates: List<MapTerritoryStateSnapshot>,
) : PublicGameStatePayload {
    companion object {
        fun from(snapshot: PublicGameStateSnapshot): MapGetResponse =
            MapGetResponse(
                lobbyCode = snapshot.lobbyCode,
                schemaVersion = snapshot.determinism.schemaVersion,
                mapHash = snapshot.determinism.mapHash,
                stateVersion = snapshot.stateVersion,
                definition = snapshot.definition,
                territoryStates = snapshot.territoryStates,
            )
    }
}

/**
 * Technischer Serializer für [MapGetResponse].
 */
object MapGetResponseSerializer :
    KSerializer<MapGetResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyTransformingSerializer(
        MapStateWireSnapshot.serializer(),
        toWire = { value ->
            MapStateWireSnapshot(
                lobbyCode = value.lobbyCode,
                schemaVersion = value.schemaVersion,
                mapHash = value.mapHash,
                stateVersion = value.stateVersion,
                definition = value.definition,
                territoryStates = value.territoryStates,
            )
        },
        fromWire = { wire ->
            MapGetResponse(
                lobbyCode = wire.lobbyCode,
                schemaVersion = wire.schemaVersion,
                mapHash = wire.mapHash,
                stateVersion = wire.stateVersion,
                definition = wire.definition,
                territoryStates = wire.territoryStates,
            )
        },
    )
