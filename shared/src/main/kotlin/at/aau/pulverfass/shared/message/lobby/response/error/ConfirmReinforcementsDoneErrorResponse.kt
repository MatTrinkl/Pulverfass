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
enum class ConfirmReinforcementsDoneErrorCode {
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
    PENDING_REINFORCEMENTS_REMAINING,
    FORCED_TRADE_REQUIRED,
}

@Serializable(with = ConfirmReinforcementsDoneErrorResponseSerializer::class)
data class ConfirmReinforcementsDoneErrorResponse(
    val code: ConfirmReinforcementsDoneErrorCode,
    val reason: String,
) : NetworkMessagePayload

object ConfirmReinforcementsDoneErrorResponseSerializer :
    KSerializer<ConfirmReinforcementsDoneErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.ConfirmReinforcementsDoneErrorResponse",
        ) {
            element("code", ConfirmReinforcementsDoneErrorCode.serializer().descriptor)
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: ConfirmReinforcementsDoneErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor,
            0,
            ConfirmReinforcementsDoneErrorCode.serializer(),
            value.code,
        )
        composite.encodeStringElement(descriptor, 1, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ConfirmReinforcementsDoneErrorResponse {
        val composite = decoder.beginStructure(descriptor)
        var code: ConfirmReinforcementsDoneErrorCode? = null
        var reason: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    code =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            ConfirmReinforcementsDoneErrorCode.serializer(),
                        )
                1 -> reason = composite.decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return ConfirmReinforcementsDoneErrorResponse(
            code = code ?: throw MissingFieldException("code", descriptor.serialName),
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
