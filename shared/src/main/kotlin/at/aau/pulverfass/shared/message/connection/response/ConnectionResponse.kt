package at.aau.pulverfass.shared.message.connection.response

import at.aau.pulverfass.shared.ids.SessionToken
import kotlinx.serialization.ExperimentalSerializationApi
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
 * Technische Initialnachricht des Servers nach erfolgreichem Verbindungsaufbau.
 *
 * @property sessionToken stabiler Token der Session für spätere Reconnect-Flows
 */
@Serializable(with = ConnectionResponseSerializer::class)
data class ConnectionResponse(
    val sessionToken: SessionToken,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [ConnectionResponse].
 */
@OptIn(ExperimentalSerializationApi::class)
object ConnectionResponseSerializer : KSerializer<ConnectionResponse> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.ConnectionResponse") {
            element("sessionToken", SessionToken.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: ConnectionResponse,
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

    override fun deserialize(decoder: Decoder): ConnectionResponse {
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
        return ConnectionResponse(
            sessionToken =
                sessionToken
                    ?: throw MissingFieldException("sessionToken", descriptor.serialName),
        )
    }

    private fun decodeSessionToken(composite: CompositeDecoder): SessionToken =
        composite.decodeSerializableElement(descriptor, 0, SessionToken.serializer())
}
