package at.aau.pulverfass.shared.message.connection.request

import at.aau.pulverfass.shared.ids.SessionToken
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

/**
 * Anfrage eines Clients, eine bestehende Session wieder an eine neue Verbindung
 * zu binden.
 *
 * @property sessionToken stabiler Session-Token des reconnectenden Clients
 */
@Serializable(with = ReconnectRequestSerializer::class)
data class ReconnectRequest(
    val sessionToken: SessionToken,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [ReconnectRequest].
 */
@OptIn(ExperimentalSerializationApi::class)
object ReconnectRequestSerializer : KSerializer<ReconnectRequest> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.ReconnectRequest",
        ) {
            element("sessionToken", SessionToken.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: ReconnectRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 0,
            serializer = SessionToken.serializer(),
            value = value.sessionToken,
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ReconnectRequest {
        val composite = decoder.beginStructure(descriptor)
        var sessionToken: SessionToken? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> sessionToken = decodeSessionToken(composite)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return ReconnectRequest(
            sessionToken =
                sessionToken
                    ?: throw MissingFieldException("sessionToken", descriptor.serialName),
        )
    }

    private fun decodeSessionToken(composite: CompositeDecoder): SessionToken =
        composite.decodeSerializableElement(descriptor, 0, SessionToken.serializer())
}
