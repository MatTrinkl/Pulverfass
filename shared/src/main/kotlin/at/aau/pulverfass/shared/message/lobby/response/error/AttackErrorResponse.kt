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
enum class AttackErrorCode {
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
    FROM_TERRITORY_NOT_OWNED,
    ATTACKING_OWN_TERRITORY,
    NOT_ADJACENT,
    INSUFFICIENT_TROOPS,
    INVALID_MOVE_AFTER_CAPTURE,
    INVALID_REQUEST,
}

@Serializable(with = AttackErrorResponseSerializer::class)
data class AttackErrorResponse(
    val code: AttackErrorCode,
    val reason: String,
    val requestId: String? = null,
) : NetworkMessagePayload

object AttackErrorResponseSerializer : KSerializer<AttackErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.AttackErrorResponse") {
            element("code", AttackErrorCode.serializer().descriptor)
            element<String>("reason")
            element<String>("requestId", isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: AttackErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor,
            0,
            AttackErrorCode.serializer(),
            value.code,
        )
        composite.encodeStringElement(descriptor, 1, value.reason)
        if (value.requestId != null) {
            composite.encodeStringElement(descriptor, 2, value.requestId)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): AttackErrorResponse {
        val composite = decoder.beginStructure(descriptor)
        var code: AttackErrorCode? = null
        var reason: String? = null
        var requestId: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    code =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            AttackErrorCode.serializer(),
                        )
                1 -> reason = composite.decodeStringElement(descriptor, 1)
                2 -> requestId = composite.decodeStringElement(descriptor, 2)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return AttackErrorResponse(
            code = code ?: throw MissingFieldException("code", descriptor.serialName),
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
            requestId = requestId,
        )
    }
}
