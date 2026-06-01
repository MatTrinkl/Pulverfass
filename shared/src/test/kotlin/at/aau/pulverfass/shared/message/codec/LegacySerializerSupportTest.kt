package at.aau.pulverfass.shared.message.codec

import at.aau.pulverfass.shared.network.message.SerializerTestDecoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacySerializerSupportTest {
    private val json = Json

    @Test
    fun `manual serializer support encodes and decodes structure consistently`() {
        val serialized = json.encodeToString(ManualStringPayloadSerializer, ManualStringPayload("legacy"))
        val deserialized =
            json.decodeFromString(
                ManualStringPayloadSerializer,
                serialized,
            )

        assertEquals("""{"value":"legacy"}""", serialized)
        assertEquals(ManualStringPayload("legacy"), deserialized)
    }

    @Test
    fun `manual serializer support reports missing and unexpected indices`() {
        val missingField =
            assertThrows(MissingFieldException::class.java) {
                ManualStringPayloadSerializer.deserialize(
                    SerializerTestDecoder(indices = intArrayOf(CompositeDecoder.DECODE_DONE)),
                )
            }
        assertTrue(missingField.message.orEmpty().contains("value"))
        assertTrue(missingField.message.orEmpty().contains("test.ManualStringPayload"))

        val unexpectedIndex =
            assertThrows(IllegalArgumentException::class.java) {
                ManualStringPayloadSerializer.deserialize(
                    SerializerTestDecoder(indices = intArrayOf(99)),
                )
        }
        assertEquals("Unexpected index 99", unexpectedIndex.message)
    }

    @Test
    fun `manual serializer support direct helpers begin and end structures`() {
        val descriptor =
            buildClassSerialDescriptor("test.DirectManualStructure") {
                element<String>("value")
            }
        val encodeMethod =
            ManualSerializerSupport::class.java.getDeclaredMethod(
                "encodeStructure",
                Encoder::class.java,
                SerialDescriptor::class.java,
                Function1::class.java,
            )
        val decodeMethod =
            ManualSerializerSupport::class.java.getDeclaredMethod(
                "decodeStructure",
                Decoder::class.java,
                SerialDescriptor::class.java,
                Function1::class.java,
            )

        val encoder = RecordingEncoder()
        var encodeBlockCalled = false
        encodeMethod.invoke(
            ManualSerializerSupport,
            encoder,
            descriptor,
            { composite: CompositeEncoder ->
                encodeBlockCalled = composite === encoder
                Unit
            },
        )

        val decoder = RecordingDecoder()
        var decodeBlockCalled = false
        val decoded =
            decodeMethod.invoke(
                ManualSerializerSupport,
                decoder,
                descriptor,
                { composite: CompositeDecoder ->
                    decodeBlockCalled = composite === decoder
                    "decoded"
                },
            )

        assertTrue(encoder.beginStructureCalled)
        assertTrue(encoder.endStructureCalled)
        assertTrue(encodeBlockCalled)
        assertTrue(decoder.beginStructureCalled)
        assertTrue(decoder.endStructureCalled)
        assertTrue(decodeBlockCalled)
        assertEquals("decoded", decoded)
    }

    @Test
    fun `legacy generated serializer preserves missing field exceptions and wraps unknown fields`() {
        val missingField = MissingFieldException("value", "test.GeneratedPayload")
        val missingSerializer = LegacyGeneratedSerializer(ThrowingSerializer<String>(missingField))

        val propagated =
            assertThrows(MissingFieldException::class.java) {
                missingSerializer.deserialize(NoopDecoder)
            }
        assertSame(missingField, propagated)

        val unknownSerializer =
            LegacyGeneratedSerializer(
                ThrowingSerializer<String>(UnknownFieldException("unknown legacy field")),
            )

        val wrapped =
            assertThrows(IllegalArgumentException::class.java) {
                unknownSerializer.deserialize(NoopDecoder)
            }
        assertEquals("unknown legacy field", wrapped.message)
        assertTrue(wrapped.cause is UnknownFieldException)
    }

    @Test
    fun `legacy transforming serializer maps values and wraps unknown fields`() {
        val serializer =
            LegacyTransformingSerializer(
                wireSerializer = String.serializer(),
                toWire = String::uppercase,
                fromWire = String::lowercase,
            )

        val serialized = json.encodeToString(serializer, "legacy")
        val deserialized = json.decodeFromString(serializer, "\"WIRE\"")

        assertEquals("\"LEGACY\"", serialized)
        assertEquals("wire", deserialized)

        val unknownSerializer =
            LegacyTransformingSerializer(
                wireSerializer = ThrowingSerializer<String>(UnknownFieldException("wire mismatch")),
                toWire = { it },
                fromWire = { it },
            )

        val wrapped =
            assertThrows(IllegalArgumentException::class.java) {
                unknownSerializer.deserialize(NoopDecoder)
            }
        assertEquals("wire mismatch", wrapped.message)
        assertTrue(wrapped.cause is UnknownFieldException)
    }

    private data class ManualStringPayload(
        val value: String,
    )

    private object ManualStringPayloadSerializer : KSerializer<ManualStringPayload> {
        override val descriptor: SerialDescriptor =
            buildClassSerialDescriptor("test.ManualStringPayload") {
                element<String>("value")
            }

        override fun serialize(
            encoder: Encoder,
            value: ManualStringPayload,
        ) {
            ManualSerializerSupport.encodeStructure(encoder, descriptor) { composite ->
                composite.encodeStringElement(descriptor, 0, value.value)
            }
        }

        override fun deserialize(decoder: Decoder): ManualStringPayload =
            ManualSerializerSupport.decodeStructure(decoder, descriptor) { composite ->
                var value: String? = null
                while (true) {
                    when (val index = composite.decodeElementIndex(descriptor)) {
                        0 -> value = composite.decodeStringElement(descriptor, 0)
                        CompositeDecoder.DECODE_DONE ->
                            return@decodeStructure ManualStringPayload(
                                value =
                                    value ?: ManualSerializerSupport.missingField("value", descriptor),
                            )

                        else -> ManualSerializerSupport.unexpectedIndex(index)
                    }
                }

                error("DECODE_DONE should have returned from decodeStructure.")
            }
    }

    private class ThrowingSerializer<T>(
        private val exception: SerializationException,
    ) : KSerializer<T> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("test.ThrowingSerializer", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: T,
        ) {
            error("serialize is not used in this test")
        }

        override fun deserialize(decoder: Decoder): T = throw exception
    }

    private class UnknownFieldException(
        message: String,
    ) : SerializationException(message)

    private object NoopDecoder : Decoder {
        override val serializersModule: SerializersModule = EmptySerializersModule()

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
            error("beginStructure is not used in this test")

        override fun decodeBoolean(): Boolean = error("Not used")

        override fun decodeByte(): Byte = error("Not used")

        override fun decodeChar(): Char = error("Not used")

        override fun decodeDouble(): Double = error("Not used")

        override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = error("Not used")

        override fun decodeFloat(): Float = error("Not used")

        override fun decodeInline(descriptor: SerialDescriptor): Decoder = this

        override fun decodeInt(): Int = error("Not used")

        override fun decodeLong(): Long = error("Not used")

        override fun decodeNotNullMark(): Boolean = true

        override fun decodeNull(): Nothing? = null

        override fun decodeShort(): Short = error("Not used")

        override fun decodeString(): String = error("Not used")
    }

    private class RecordingEncoder : Encoder, CompositeEncoder {
        var beginStructureCalled = false
        var endStructureCalled = false

        override val serializersModule: SerializersModule = EmptySerializersModule()

        override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
            beginStructureCalled = true
            return this
        }

        override fun endStructure(descriptor: SerialDescriptor) {
            endStructureCalled = true
        }

        override fun encodeBoolean(value: Boolean) = error("Not used")

        override fun encodeByte(value: Byte) = error("Not used")

        override fun encodeChar(value: Char) = error("Not used")

        override fun encodeDouble(value: Double) = error("Not used")

        override fun encodeEnum(
            enumDescriptor: SerialDescriptor,
            index: Int,
        ) = error("Not used")

        override fun encodeFloat(value: Float) = error("Not used")

        override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

        override fun encodeInt(value: Int) = error("Not used")

        override fun encodeLong(value: Long) = error("Not used")

        override fun encodeNotNullMark() = Unit

        override fun encodeNull() = Unit

        override fun encodeShort(value: Short) = error("Not used")

        override fun encodeString(value: String) = Unit

        override fun encodeBooleanElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Boolean,
        ) = error("Not used")

        override fun encodeByteElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Byte,
        ) = error("Not used")

        override fun encodeCharElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Char,
        ) = error("Not used")

        override fun encodeDoubleElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Double,
        ) = error("Not used")

        override fun encodeFloatElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Float,
        ) = error("Not used")

        override fun encodeInlineElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Encoder = this

        override fun encodeIntElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Int,
        ) = error("Not used")

        override fun encodeLongElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Long,
        ) = error("Not used")

        override fun <T : Any> encodeNullableSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            serializer: SerializationStrategy<T>,
            value: T?,
        ) = error("Not used")

        override fun <T> encodeSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            serializer: SerializationStrategy<T>,
            value: T,
        ) = error("Not used")

        override fun encodeShortElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Short,
        ) = error("Not used")

        override fun encodeStringElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: String,
        ) = Unit
    }

    private class RecordingDecoder : Decoder, CompositeDecoder {
        var beginStructureCalled = false
        var endStructureCalled = false

        override val serializersModule: SerializersModule = EmptySerializersModule()

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
            beginStructureCalled = true
            return this
        }

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int = CompositeDecoder.DECODE_DONE

        override fun endStructure(descriptor: SerialDescriptor) {
            endStructureCalled = true
        }

        override fun decodeSequentially(): Boolean = false

        override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = 0

        override fun decodeBoolean(): Boolean = error("Not used")

        override fun decodeByte(): Byte = error("Not used")

        override fun decodeChar(): Char = error("Not used")

        override fun decodeDouble(): Double = error("Not used")

        override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = error("Not used")

        override fun decodeFloat(): Float = error("Not used")

        override fun decodeInline(descriptor: SerialDescriptor): Decoder = this

        override fun decodeInt(): Int = error("Not used")

        override fun decodeLong(): Long = error("Not used")

        override fun decodeNotNullMark(): Boolean = true

        override fun decodeNull(): Nothing? = null

        override fun decodeShort(): Short = error("Not used")

        override fun decodeString(): String = error("Not used")

        override fun decodeBooleanElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Boolean = error("Not used")

        override fun decodeByteElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Byte = error("Not used")

        override fun decodeCharElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Char = error("Not used")

        override fun decodeDoubleElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Double = error("Not used")

        override fun decodeFloatElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Float = error("Not used")

        override fun decodeInlineElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Decoder = this

        override fun decodeIntElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Int = error("Not used")

        override fun decodeLongElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Long = error("Not used")

        override fun <T> decodeSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            deserializer: kotlinx.serialization.DeserializationStrategy<T>,
            previousValue: T?,
        ): T = error("Not used")

        override fun <T : Any> decodeNullableSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            deserializer: kotlinx.serialization.DeserializationStrategy<T?>,
            previousValue: T?,
        ): T? = error("Not used")

        override fun decodeShortElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Short = error("Not used")

        override fun decodeStringElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): String = error("Not used")
    }
}
