package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = FortifyMoveRequestSerializer::class)
data class FortifyMoveRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val fromTerritoryId: TerritoryId,
    val toTerritoryId: TerritoryId,
    val troopCount: Int,
) : NetworkMessagePayload {
    init {
        require(troopCount > 0) {
            "FortifyMoveRequest.troopCount muss positiv sein, war aber $troopCount."
        }
        require(fromTerritoryId != toTerritoryId) {
            "FortifyMoveRequest benötigt unterschiedliche Territorien."
        }
    }
}

object FortifyMoveRequestSerializer : KSerializer<FortifyMoveRequest> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.FortifyMoveRequest",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element("fromTerritoryId", TerritoryId.serializer().descriptor)
            element("toTerritoryId", TerritoryId.serializer().descriptor)
            element<Int>("troopCount")
        }

    override fun serialize(
        encoder: Encoder,
        value: FortifyMoveRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.playerId)
        composite.encodeSerializableElement(
            descriptor,
            2,
            TerritoryId.serializer(),
            value.fromTerritoryId,
        )
        composite.encodeSerializableElement(
            descriptor,
            3,
            TerritoryId.serializer(),
            value.toTerritoryId,
        )
        composite.encodeIntElement(descriptor, 4, value.troopCount)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): FortifyMoveRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null
        var fromTerritoryId: TerritoryId? = null
        var toTerritoryId: TerritoryId? = null
        var troopCount: Int? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            LobbyCode.serializer(),
                        )
                1 ->
                    playerId =
                        composite.decodeSerializableElement(
                            descriptor,
                            1,
                            PlayerId.serializer(),
                        )
                2 ->
                    fromTerritoryId =
                        composite.decodeSerializableElement(
                            descriptor,
                            2,
                            TerritoryId.serializer(),
                        )
                3 ->
                    toTerritoryId =
                        composite.decodeSerializableElement(
                            descriptor,
                            3,
                            TerritoryId.serializer(),
                        )
                4 -> troopCount = composite.decodeIntElement(descriptor, 4)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return FortifyMoveRequest(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerId = playerId ?: throw MissingFieldException("playerId", descriptor.serialName),
            fromTerritoryId =
                fromTerritoryId
                    ?: throw MissingFieldException("fromTerritoryId", descriptor.serialName),
            toTerritoryId =
                toTerritoryId
                    ?: throw MissingFieldException("toTerritoryId", descriptor.serialName),
            troopCount =
                troopCount ?: throw MissingFieldException("troopCount", descriptor.serialName),
        )
    }
}
