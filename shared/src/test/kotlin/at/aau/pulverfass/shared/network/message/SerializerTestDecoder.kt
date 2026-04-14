package at.aau.pulverfass.shared.network.message

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

internal class SerializerTestDecoder(
    private val indices: IntArray,
    private val stringElements: Map<Int, String> = emptyMap(),
    private val scalarString: String? = null,
) : Decoder, CompositeDecoder {
    private var indexPointer = 0

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = this

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        indices.getOrElse(indexPointer++) { CompositeDecoder.DECODE_DONE }

    override fun endStructure(descriptor: SerialDescriptor) = Unit

    override fun decodeSequentially(): Boolean = false

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = 0

    override fun decodeBoolean(): Boolean = error("Not used in serializer coverage tests.")

    override fun decodeByte(): Byte = error("Not used in serializer coverage tests.")

    override fun decodeChar(): Char = error("Not used in serializer coverage tests.")

    override fun decodeDouble(): Double = error("Not used in serializer coverage tests.")

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        error(
            "Not used in serializer coverage tests.",
        )

    override fun decodeFloat(): Float = error("Not used in serializer coverage tests.")

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this

    override fun decodeInt(): Int = error("Not used in serializer coverage tests.")

    override fun decodeLong(): Long = error("Not used in serializer coverage tests.")

    override fun decodeNotNullMark(): Boolean = true

    override fun decodeNull(): Nothing? = null

    override fun decodeShort(): Short = error("Not used in serializer coverage tests.")

    override fun decodeString(): String = scalarString ?: error("No scalar string configured.")

    override fun decodeBooleanElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Boolean = error("Not used in serializer coverage tests.")

    override fun decodeByteElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Byte = error("Not used in serializer coverage tests.")

    override fun decodeCharElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Char = error("Not used in serializer coverage tests.")

    override fun decodeDoubleElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Double = error("Not used in serializer coverage tests.")

    override fun decodeFloatElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Float = error("Not used in serializer coverage tests.")

    override fun decodeInlineElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Decoder = this

    override fun decodeIntElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Int = error("Not used in serializer coverage tests.")

    override fun decodeLongElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Long = error("Not used in serializer coverage tests.")

    override fun decodeShortElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Short = error("Not used in serializer coverage tests.")

    override fun decodeStringElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): String = stringElements[index] ?: error("No string value configured for index $index.")

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?,
    ): T = deserializer.deserialize(this)

    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?,
    ): T? = deserializer.deserialize(this)
}
