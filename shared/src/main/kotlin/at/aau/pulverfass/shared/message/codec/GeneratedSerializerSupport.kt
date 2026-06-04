package at.aau.pulverfass.shared.message.codec

import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Delegiert an generierte Serializer, hält aber das bisherige Fehlerverhalten
 * für unbekannte Indizes bei direkten Serializer-Aufrufen stabil.
 */
internal class LegacyGeneratedSerializer<T>(
    private val delegate: KSerializer<T>,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): T =
        try {
            delegate.deserialize(decoder)
        } catch (exception: SerializationException) {
            if (
                exception is MissingFieldException ||
                exception::class.simpleName != "UnknownFieldException"
            ) {
                throw exception
            }
            throw IllegalArgumentException(exception.message ?: "Unexpected index", exception)
        }
}

/**
 * Delegiert an einen generierten Wire-Serializer und mappt zwischen
 * Transportmodell und Fachtyp.
 */
internal class LegacyTransformingSerializer<T, Wire>(
    private val wireSerializer: KSerializer<Wire>,
    private val toWire: (T) -> Wire,
    private val fromWire: (Wire) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = wireSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) = wireSerializer.serialize(encoder, toWire(value))

    override fun deserialize(decoder: Decoder): T =
        try {
            fromWire(wireSerializer.deserialize(decoder))
        } catch (exception: SerializationException) {
            if (
                exception is MissingFieldException ||
                exception::class.simpleName != "UnknownFieldException"
            ) {
                throw exception
            }
            throw IllegalArgumentException(exception.message ?: "Unexpected index", exception)
        }
}
