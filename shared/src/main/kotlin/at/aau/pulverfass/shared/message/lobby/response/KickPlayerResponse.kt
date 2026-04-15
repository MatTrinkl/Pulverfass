package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Erfolgreiche Antwort des Servers auf eine Kick-Anfrage.
 *
 * Bestätigt, dass der Spieler erfolgreich aus der Lobby entfernt wurde.
 */
@Serializable(with = KickPlayerResponseSerializer::class)
data class KickPlayerResponse(
    val success: Boolean = true,
) : NetworkMessagePayload

object KickPlayerResponseSerializer : KSerializer<KickPlayerResponse> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.KickPlayerResponse") {
        }

    override fun serialize(
        encoder: Encoder,
        value: KickPlayerResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): KickPlayerResponse {
        val composite = decoder.beginStructure(descriptor)

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return KickPlayerResponse()
    }
}
