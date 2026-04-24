package at.aau.pulverfass.shared.message.lobby.response.error

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
 * Typisierte Fehlercodes für fehlgeschlagene Turn-State-Snapshot-Anfragen.
 */
@Serializable
enum class TurnStateGetErrorCode {
    GAME_NOT_FOUND,
    TURN_STATE_NOT_READY,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Turn-State-Snapshot-Anfrage.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable(with = TurnStateGetErrorResponseSerializer::class)
data class TurnStateGetErrorResponse(
    val code: TurnStateGetErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [TurnStateGetErrorResponse].
 */
object TurnStateGetErrorResponseSerializer : KSerializer<TurnStateGetErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.TurnStateGetErrorResponse",
        ) {
            element("code", TurnStateGetErrorCode.serializer().descriptor)
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: TurnStateGetErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor,
            0,
            TurnStateGetErrorCode.serializer(),
            value.code,
        )
        composite.encodeStringElement(descriptor, 1, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): TurnStateGetErrorResponse {
        val composite = decoder.beginStructure(descriptor)
        var code: TurnStateGetErrorCode? = null
        var reason: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    code =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            TurnStateGetErrorCode.serializer(),
                        )
                1 -> reason = composite.decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return TurnStateGetErrorResponse(
            code = code ?: throw MissingFieldException("code", descriptor.serialName),
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
