package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class TradeInCardsErrorCode {
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
    CARDS_NOT_OWNED,
    INVALID_SET,
    INVALID_REQUEST,
}

@Serializable(with = TradeInCardsErrorResponseSerializer::class)
data class TradeInCardsErrorResponse(
    val code: TradeInCardsErrorCode,
    val reason: String,
) : NetworkMessagePayload

object TradeInCardsErrorResponseSerializer : KSerializer<TradeInCardsErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.TradeInCardsErrorResponse",
        ) {
            element("code", TradeInCardsErrorCode.serializer().descriptor)
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: TradeInCardsErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor,
            0,
            TradeInCardsErrorCode.serializer(),
            value.code,
        )
        composite.encodeStringElement(descriptor, 1, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): TradeInCardsErrorResponse {
        val composite = decoder.beginStructure(descriptor)
        var code: TradeInCardsErrorCode? = null
        var reason: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    code =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            TradeInCardsErrorCode.serializer(),
                        )
                1 -> reason = composite.decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return TradeInCardsErrorResponse(
            code = code ?: throw MissingFieldException("code", descriptor.serialName),
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
