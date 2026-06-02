package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CharacterSelectErrorResponseSerializer::class)
data class CharacterSelectErrorResponse(
    val reason: String,
) : NetworkMessagePayload

@OptIn(ExperimentalSerializationApi::class)
object CharacterSelectErrorResponseSerializer : KSerializer<CharacterSelectErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.message.lobby.response.error.CharacterSelectErrorResponse",
        ) {
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: CharacterSelectErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): CharacterSelectErrorResponse {
        val composite = decoder.beginStructure(descriptor)
        val serialName = descriptor.serialName
        var reason: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> reason = composite.decodeStringElement(descriptor, 0)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return CharacterSelectErrorResponse(
            reason = reason ?: throw MissingFieldException("reason", serialName),
        )
    }
}
