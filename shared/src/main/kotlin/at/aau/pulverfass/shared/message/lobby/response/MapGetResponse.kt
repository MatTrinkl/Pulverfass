package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.MapStateWireSnapshot
import at.aau.pulverfass.shared.message.lobby.event.PublicGameStatePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
object MapGetResponseSerializer : KSerializer<MapGetResponse> {
    private val wireSerializer = MapStateWireSnapshot.serializer()

    override val descriptor = wireSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: MapGetResponse,
    ) {
        wireSerializer.serialize(
            encoder,
            MapStateWireSnapshot(
                lobbyCode = value.lobbyCode,
                schemaVersion = value.schemaVersion,
                mapHash = value.mapHash,
                stateVersion = value.stateVersion,
                definition = value.definition,
                territoryStates = value.territoryStates,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): MapGetResponse {
        val wire = wireSerializer.deserialize(decoder)
        return MapGetResponse(
            lobbyCode = wire.lobbyCode,
            schemaVersion = wire.schemaVersion,
            mapHash = wire.mapHash,
            stateVersion = wire.stateVersion,
            definition = wire.definition,
            territoryStates = wire.territoryStates,
        )
    }
}
