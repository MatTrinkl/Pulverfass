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
 * Fehlantwort des Servers auf eine fehlgeschlagene Kick-Anfrage.
 *
 * @property reason Anzeigegrund für den Client (z.B. "not_owner", "player_not_found")
 */
@Serializable(with = KickPlayerErrorResponseSerializer::class)
data class KickPlayerErrorResponse(
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [KickPlayerErrorResponse].
 */
object KickPlayerErrorResponseSerializer : KSerializer<KickPlayerErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.KickPlayerErrorResponse",
        ) {
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: KickPlayerErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): KickPlayerErrorResponse {
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
        return KickPlayerErrorResponse(
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
