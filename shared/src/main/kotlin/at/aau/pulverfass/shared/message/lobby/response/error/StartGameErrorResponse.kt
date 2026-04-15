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
 * Fehlantwort des Servers auf eine fehlgeschlagene StartGame-Anfrage.
 *
 * @property reason Anzeigegrund für den Client (z.B. "not_owner", "not_enough_players")
 */
@Serializable(with = StartGameErrorResponseSerializer::class)
data class StartGameErrorResponse(
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [StartGameErrorResponse].
 */
object StartGameErrorResponseSerializer : KSerializer<StartGameErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.StartGameErrorResponse",
        ) {
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: StartGameErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): StartGameErrorResponse {
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
        return StartGameErrorResponse(
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
