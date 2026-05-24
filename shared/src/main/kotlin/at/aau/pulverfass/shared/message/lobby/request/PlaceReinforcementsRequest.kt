package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PlaceReinforcementsRequestSerializer::class)
data class PlaceReinforcementsRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val placements: List<TerritoryPlacement>,
) : NetworkMessagePayload

@Serializable(with = TerritoryPlacementSerializer::class)
data class TerritoryPlacement(
    val territoryId: TerritoryId,
    val amount: Int,
) {
    init {
        require(amount > 0) {
            "TerritoryPlacement.amount muss positiv sein, war aber $amount."
        }
    }
}

object PlaceReinforcementsRequestSerializer : KSerializer<PlaceReinforcementsRequest> {
    private val placementsSerializer = ListSerializer(TerritoryPlacement.serializer())

    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.PlaceReinforcementsRequest",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element("placements", placementsSerializer.descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: PlaceReinforcementsRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.playerId)
        composite.encodeSerializableElement(
            descriptor,
            2,
            placementsSerializer,
            value.placements,
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PlaceReinforcementsRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null
        var placements: List<TerritoryPlacement>? = null

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
                    placements =
                        composite.decodeSerializableElement(
                            descriptor,
                            2,
                            placementsSerializer,
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return PlaceReinforcementsRequest(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerId = playerId ?: throw MissingFieldException("playerId", descriptor.serialName),
            placements =
                placements
                    ?: throw MissingFieldException("placements", descriptor.serialName),
        )
    }
}

object TerritoryPlacementSerializer : KSerializer<TerritoryPlacement> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.TerritoryPlacement") {
            element("territoryId", TerritoryId.serializer().descriptor)
            element<Int>("amount")
        }

    override fun serialize(
        encoder: Encoder,
        value: TerritoryPlacement,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor,
            0,
            TerritoryId.serializer(),
            value.territoryId,
        )
        composite.encodeIntElement(descriptor, 1, value.amount)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): TerritoryPlacement {
        val composite = decoder.beginStructure(descriptor)
        var territoryId: TerritoryId? = null
        var amount: Int? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    territoryId =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            TerritoryId.serializer(),
                        )
                1 -> amount = composite.decodeIntElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return TerritoryPlacement(
            territoryId =
                territoryId
                    ?: throw MissingFieldException("territoryId", descriptor.serialName),
            amount = amount ?: throw MissingFieldException("amount", descriptor.serialName),
        )
    }
}
