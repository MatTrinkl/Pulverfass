package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.event.PublicGameStatePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
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
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.MapGetResponse") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element<Int>("schemaVersion")
            element<String>("mapHash")
            element<Long>("stateVersion")
            element("definition", MapDefinitionSnapshot.serializer().descriptor)
            element(
                "territoryStates",
                kotlinx.serialization.builtins.ListSerializer(
                    MapTerritoryStateSnapshot.serializer(),
                ).descriptor,
            )
        }

    override fun serialize(
        encoder: Encoder,
        value: MapGetResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeIntElement(descriptor, 1, value.schemaVersion)
        composite.encodeStringElement(descriptor, 2, value.mapHash)
        composite.encodeLongElement(descriptor, 3, value.stateVersion)
        composite.encodeSerializableElement(
            descriptor,
            4,
            MapDefinitionSnapshot.serializer(),
            value.definition,
        )
        composite.encodeSerializableElement(
            descriptor,
            5,
            kotlinx.serialization.builtins.ListSerializer(MapTerritoryStateSnapshot.serializer()),
            value.territoryStates,
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): MapGetResponse {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var schemaVersion: Int? = null
        var mapHash: String? = null
        var stateVersion: Long? = null
        var definition: MapDefinitionSnapshot? = null
        var territoryStates: List<MapTerritoryStateSnapshot>? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            LobbyCode.serializer(),
                        )
                1 -> schemaVersion = composite.decodeIntElement(descriptor, 1)
                2 -> mapHash = composite.decodeStringElement(descriptor, 2)
                3 -> stateVersion = composite.decodeLongElement(descriptor, 3)
                4 ->
                    definition =
                        composite.decodeSerializableElement(
                            descriptor,
                            4,
                            MapDefinitionSnapshot.serializer(),
                        )
                5 ->
                    territoryStates =
                        composite.decodeSerializableElement(
                            descriptor,
                            5,
                            kotlinx.serialization.builtins.ListSerializer(
                                MapTerritoryStateSnapshot.serializer(),
                            ),
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return MapGetResponse(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            schemaVersion =
                schemaVersion
                    ?: throw MissingFieldException("schemaVersion", descriptor.serialName),
            mapHash = mapHash ?: throw MissingFieldException("mapHash", descriptor.serialName),
            stateVersion =
                stateVersion
                    ?: throw MissingFieldException("stateVersion", descriptor.serialName),
            definition =
                definition
                    ?: throw MissingFieldException("definition", descriptor.serialName),
            territoryStates =
                territoryStates
                    ?: throw MissingFieldException("territoryStates", descriptor.serialName),
        )
    }
}
