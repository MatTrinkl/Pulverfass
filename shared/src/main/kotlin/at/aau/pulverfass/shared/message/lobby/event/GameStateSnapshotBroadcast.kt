package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicGameStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Vollständiger öffentlicher GameState-Snapshot für Self-Healing beim Turnwechsel.
 *
 * Der Broadcast enthält ausschließlich öffentliche Informationen und soll Clients
 * nach einem Spielerwechsel auf einen konsistenten Stand zurückführen.
 */
@Serializable(with = GameStateSnapshotBroadcastSerializer::class)
data class GameStateSnapshotBroadcast(
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val determinism: PublicDeterminismMetadataSnapshot,
    val turnState: PublicTurnStateSnapshot,
    val definition: MapDefinitionSnapshot,
    val territoryStates: List<MapTerritoryStateSnapshot>,
) : PublicGameStatePayload {
    companion object {
        fun from(snapshot: PublicGameStateSnapshot): GameStateSnapshotBroadcast =
            GameStateSnapshotBroadcast(
                lobbyCode = snapshot.lobbyCode,
                stateVersion = snapshot.stateVersion,
                determinism = snapshot.determinism,
                turnState = snapshot.turnState,
                definition = snapshot.definition,
                territoryStates = snapshot.territoryStates,
            )
    }
}

/**
 * Technischer Serializer für [GameStateSnapshotBroadcast].
 */
object GameStateSnapshotBroadcastSerializer : KSerializer<GameStateSnapshotBroadcast> {
    private val territoryStatesSerializer = ListSerializer(MapTerritoryStateSnapshot.serializer())

    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.GameStateSnapshotBroadcast",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element<Long>("stateVersion")
            element("determinism", PublicDeterminismMetadataSnapshot.serializer().descriptor)
            element("turnState", PublicTurnStateSnapshot.serializer().descriptor)
            element("definition", MapDefinitionSnapshot.serializer().descriptor)
            element("territoryStates", territoryStatesSerializer.descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: GameStateSnapshotBroadcast,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeLongElement(descriptor, 1, value.stateVersion)
        composite.encodeSerializableElement(
            descriptor,
            2,
            PublicDeterminismMetadataSnapshot.serializer(),
            value.determinism,
        )
        composite.encodeSerializableElement(
            descriptor,
            3,
            PublicTurnStateSnapshot.serializer(),
            value.turnState,
        )
        composite.encodeSerializableElement(
            descriptor,
            4,
            MapDefinitionSnapshot.serializer(),
            value.definition,
        )
        composite.encodeSerializableElement(
            descriptor,
            5,
            territoryStatesSerializer,
            value.territoryStates,
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): GameStateSnapshotBroadcast {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var stateVersion: Long? = null
        var determinism: PublicDeterminismMetadataSnapshot? = null
        var turnState: PublicTurnStateSnapshot? = null
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
                1 -> stateVersion = composite.decodeLongElement(descriptor, 1)
                2 ->
                    determinism =
                        composite.decodeSerializableElement(
                            descriptor,
                            2,
                            PublicDeterminismMetadataSnapshot.serializer(),
                        )
                3 ->
                    turnState =
                        composite.decodeSerializableElement(
                            descriptor,
                            3,
                            PublicTurnStateSnapshot.serializer(),
                        )
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
                            territoryStatesSerializer,
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return GameStateSnapshotBroadcast(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            stateVersion =
                stateVersion
                    ?: throw MissingFieldException("stateVersion", descriptor.serialName),
            determinism =
                determinism
                    ?: throw MissingFieldException("determinism", descriptor.serialName),
            turnState =
                turnState
                    ?: throw MissingFieldException("turnState", descriptor.serialName),
            definition =
                definition
                    ?: throw MissingFieldException("definition", descriptor.serialName),
            territoryStates =
                territoryStates
                    ?: throw MissingFieldException("territoryStates", descriptor.serialName),
        )
    }
}
