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
enum class FortifyMoveErrorCode {
    GAME_NOT_FOUND,
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    WRONG_PHASE,
    TERRITORY_NOT_OWNED,
    NO_PATH,
    INSUFFICIENT_TROOPS,
    FORTIFY_ALREADY_USED,
}

@Serializable(with = FortifyMoveErrorResponseSerializer::class)
data class FortifyMoveErrorResponse(
    val code: FortifyMoveErrorCode,
    val reason: String,
) : NetworkMessagePayload

object FortifyMoveErrorResponseSerializer : KSerializer<FortifyMoveErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.FortifyMoveErrorResponse",
        ) {
            element("code", FortifyMoveErrorCode.serializer().descriptor)
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: FortifyMoveErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor,
            0,
            FortifyMoveErrorCode.serializer(),
            value.code,
        )
        composite.encodeStringElement(descriptor, 1, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): FortifyMoveErrorResponse {
        val composite = decoder.beginStructure(descriptor)
        var code: FortifyMoveErrorCode? = null
        var reason: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    code =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            FortifyMoveErrorCode.serializer(),
                        )
                1 -> reason = composite.decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return FortifyMoveErrorResponse(
            code = code ?: throw MissingFieldException("code", descriptor.serialName),
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
