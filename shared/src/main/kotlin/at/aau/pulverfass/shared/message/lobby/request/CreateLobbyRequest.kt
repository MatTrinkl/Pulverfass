package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Anfrage eines Clients an den Server, eine neue Lobby anzulegen.
 *
 * Die Anfrage selbst trägt keine zusätzlichen Parameter; alle notwendigen
 * serverseitigen Daten werden intern ermittelt.
 */
@Serializable(with = CreateLobbyRequestSerializer::class)
data object CreateLobbyRequest : NetworkMessagePayload

/**
 * Technischer Serializer für [CreateLobbyRequest].
 */
object CreateLobbyRequestSerializer : KSerializer<CreateLobbyRequest> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.CreateLobbyRequest")

    override fun serialize(
        encoder: Encoder,
        value: CreateLobbyRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): CreateLobbyRequest {
        val composite = decoder.beginStructure(descriptor)

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return CreateLobbyRequest
    }
}
