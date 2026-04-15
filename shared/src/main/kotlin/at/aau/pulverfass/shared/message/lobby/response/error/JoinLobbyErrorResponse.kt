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
 * Fehlantwort des Servers auf einen nicht erfolgreichen Lobby-Join.
 *
 * @property reason Anzeigegrund für den Client
 */
@Serializable(with = JoinLobbyErrorResponseSerializer::class)
data class JoinLobbyErrorResponse(
    val reason: String,
) : NetworkMessagePayload

object JoinLobbyErrorResponseSerializer : KSerializer<JoinLobbyErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.JoinLobbyErrorResponse",
        ) {
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: JoinLobbyErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): JoinLobbyErrorResponse {
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
        return JoinLobbyErrorResponse(
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
