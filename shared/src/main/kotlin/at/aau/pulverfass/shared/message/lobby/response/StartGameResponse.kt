package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Erfolgreiche Antwort des Servers auf eine StartGame-Anfrage.
 *
 * Bestätigt, dass das Spiel erfolgreich gestartet wurde.
 */
@Serializable(with = StartGameResponseSerializer::class)
data class StartGameResponse(
    val success: Boolean = true,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [StartGameResponse].
 */
object StartGameResponseSerializer : KSerializer<StartGameResponse> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.StartGameResponse") {
        }

    override fun serialize(
        encoder: Encoder,
        value: StartGameResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): StartGameResponse {
        val composite = decoder.beginStructure(descriptor)

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return StartGameResponse()
    }
}
