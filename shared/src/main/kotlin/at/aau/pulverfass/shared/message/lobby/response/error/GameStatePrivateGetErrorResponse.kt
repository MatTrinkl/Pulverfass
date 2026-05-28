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
 * Typisierte Fehlercodes für fehlgeschlagene private GameState-Snapshot-Anfragen.
 */
@Serializable
enum class GameStatePrivateGetErrorCode {
    GAME_NOT_FOUND,
    NOT_IN_GAME,
    REQUESTER_MISMATCH,
    PAYLOAD_TOO_LARGE,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche private Snapshot-Anfrage.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable(with = GameStatePrivateGetErrorResponseSerializer::class)
data class GameStatePrivateGetErrorResponse(
    val code: GameStatePrivateGetErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [GameStatePrivateGetErrorResponse].
 */
object GameStatePrivateGetErrorResponseSerializer : KSerializer<GameStatePrivateGetErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.GameStatePrivateGetErrorResponse",
        ) {
            element("code", GameStatePrivateGetErrorCode.serializer().descriptor)
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: GameStatePrivateGetErrorResponse,
    ) {
        ManualSerializerSupport.encodeStructure(encoder, descriptor) { composite ->
            composite.encodeSerializableElement(
                descriptor,
                0,
                GameStatePrivateGetErrorCode.serializer(),
                value.code,
            )
            composite.encodeStringElement(descriptor, 1, value.reason)
        }
    }

    override fun deserialize(decoder: Decoder): GameStatePrivateGetErrorResponse =
        ManualSerializerSupport.decodeStructure(decoder, descriptor) { composite ->
            var code: GameStatePrivateGetErrorCode? = null
            var reason: String? = null

            loop@ while (true) {
                when (val index = composite.decodeElementIndex(descriptor)) {
                    0 ->
                        code =
                            composite.decodeSerializableElement(
                                descriptor,
                                0,
                                GameStatePrivateGetErrorCode.serializer(),
                            )
                    1 -> reason = composite.decodeStringElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break@loop
                    else -> ManualSerializerSupport.unexpectedIndex(index)
                }
            }

            GameStatePrivateGetErrorResponse(
                code = code ?: ManualSerializerSupport.missingField("code", descriptor),
                reason = reason ?: ManualSerializerSupport.missingField("reason", descriptor),
            )
        }
}
