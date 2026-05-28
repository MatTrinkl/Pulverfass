package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.codec.ManualSerializerSupport
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Typisierte Fehlercodes für fehlgeschlagene Map-Snapshot-Anfragen.
 */
@Serializable
enum class MapGetErrorCode {
    GAME_NOT_FOUND,
    NOT_IN_GAME,
    MAP_NOT_READY,
    PAYLOAD_TOO_LARGE,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Map-Snapshot-Anfrage.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable(with = MapGetErrorResponseSerializer::class)
data class MapGetErrorResponse(
    val code: MapGetErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [MapGetErrorResponse].
 */
object MapGetErrorResponseSerializer : KSerializer<MapGetErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.MapGetErrorResponse") {
            element("code", MapGetErrorCode.serializer().descriptor)
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: MapGetErrorResponse,
    ) {
        ManualSerializerSupport.encodeStructure(encoder, descriptor) { composite ->
            composite.encodeSerializableElement(
                descriptor,
                0,
                MapGetErrorCode.serializer(),
                value.code,
            )
            composite.encodeStringElement(descriptor, 1, value.reason)
        }
    }

    override fun deserialize(decoder: Decoder): MapGetErrorResponse =
        ManualSerializerSupport.decodeStructure(decoder, descriptor) { composite ->
            var code: MapGetErrorCode? = null
            var reason: String? = null

            loop@ while (true) {
                when (val index = composite.decodeElementIndex(descriptor)) {
                    0 ->
                        code =
                            composite.decodeSerializableElement(
                                descriptor,
                                0,
                                MapGetErrorCode.serializer(),
                            )
                    1 -> reason = composite.decodeStringElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break@loop
                    else -> ManualSerializerSupport.unexpectedIndex(index)
                }
            }

            MapGetErrorResponse(
                code = code ?: ManualSerializerSupport.missingField("code", descriptor),
                reason = reason ?: ManualSerializerSupport.missingField("reason", descriptor),
            )
        }
}
