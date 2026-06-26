package at.aau.pulverfass.shared.message.codec

import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Gemeinsame Helfer fuer handgeschriebene Netzwerk-Serializer.
 *
 * Die Utilities reduzieren Boilerplate und halten Fehlerverhalten, Struktur-
 * Abschluss und Meldungen konsistent ueber mehrere DTOs hinweg.
 */
internal object ManualSerializerSupport {
    inline fun encodeStructure(
        encoder: Encoder,
        descriptor: SerialDescriptor,
        block: (CompositeEncoder) -> Unit,
    ) {
        val composite = encoder.beginStructure(descriptor)
        block(composite)
        composite.endStructure(descriptor)
    }

    inline fun <T> decodeStructure(
        decoder: Decoder,
        descriptor: SerialDescriptor,
        block: (CompositeDecoder) -> T,
    ): T {
        val composite = decoder.beginStructure(descriptor)
        val result = block(composite)
        composite.endStructure(descriptor)
        return result
    }

    fun missingField(
        fieldName: String,
        descriptor: SerialDescriptor,
    ): Nothing =
        throw MissingFieldException(
            fieldName,
            descriptor.serialName,
        )

    fun unexpectedIndex(index: Int): Nothing =
        throw IllegalArgumentException("Unexpected index $index")
}
