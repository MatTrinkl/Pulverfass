package at.aau.pulverfass.shared.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for [MessageType] that encodes the stable numeric [MessageType.id]
 * instead of the enum name, to match the protocol's `typeId` field.
 */
object MessageTypeAsIdSerializer : KSerializer<MessageType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MessageType", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: MessageType) {
        encoder.encodeInt(value.id)
    }

    override fun deserialize(decoder: Decoder): MessageType {
        val id = decoder.decodeInt()
        return MessageType.values().first { it.id == id }
    }
}

@Serializable
data class MessageHeader(
    @Serializable(with = MessageTypeAsIdSerializer::class)
    val type: MessageType,
)
