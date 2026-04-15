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

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Lobby-Erstellung.
 *
 * @property reason Anzeigegrund für den Client
 */
@Serializable(with = CreateLobbyErrorResponseSerializer::class)
data class CreateLobbyErrorResponse(
    val reason: String,
) : NetworkMessagePayload

object CreateLobbyErrorResponseSerializer : KSerializer<CreateLobbyErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.CreateLobbyErrorResponse",
        ) {
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: CreateLobbyErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): CreateLobbyErrorResponse {
        val composite = decoder.beginStructure(descriptor)
        var reason: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> reason = composite.decodeStringElement(descriptor, 0)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return CreateLobbyErrorResponse(
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
