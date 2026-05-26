package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
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

@Serializable(with = TradeInCardsRequestSerializer::class)
data class TradeInCardsRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val cardIds: List<CardId>,
) : NetworkMessagePayload {
    init {
        require(cardIds.size == 3) {
            "TradeInCardsRequest.cardIds muss genau 3 Karten enthalten."
        }
        require(cardIds.distinct().size == cardIds.size) {
            "TradeInCardsRequest.cardIds darf keine Duplikate enthalten."
        }
    }
}

object TradeInCardsRequestSerializer : KSerializer<TradeInCardsRequest> {
    private val cardIdsSerializer = ListSerializer(CardId.serializer())

    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.TradeInCardsRequest",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element("cardIds", cardIdsSerializer.descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: TradeInCardsRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.playerId)
        composite.encodeSerializableElement(descriptor, 2, cardIdsSerializer, value.cardIds)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): TradeInCardsRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null
        var cardIds: List<CardId>? = null

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
                    cardIds =
                        composite.decodeSerializableElement(
                            descriptor,
                            2,
                            cardIdsSerializer,
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return TradeInCardsRequest(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerId = playerId ?: throw MissingFieldException("playerId", descriptor.serialName),
            cardIds = cardIds ?: throw MissingFieldException("cardIds", descriptor.serialName),
        )
    }
}
