package at.aau.pulverfass.shared.message.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Technischer Nachrichtenkopf des Protokolls.
 *
 * Der Header enthält aktuell nur den [MessageType] und legt damit fest, wie die
 * Payload zu interpretieren ist.
 *
 * @property type Protokolltyp der Nachricht
 */
@Serializable(with = MessageHeaderSerializer::class)
data class MessageHeader(
    val type: MessageType,
)

object MessageHeaderSerializer : KSerializer<MessageHeader> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.MessageHeader") {
            element("type", MessageType.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: MessageHeader,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 0,
            serializer = MessageType.serializer(),
            value = value.type,
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): MessageHeader {
        val composite = decoder.beginStructure(descriptor)
        var type: MessageType? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    type =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            MessageType.serializer(),
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return MessageHeader(
            type = type ?: throw MissingFieldException("type", descriptor.serialName),
        )
    }
}
