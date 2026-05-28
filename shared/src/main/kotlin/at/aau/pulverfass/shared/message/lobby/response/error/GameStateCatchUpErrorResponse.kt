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
 * Typisierte Fehlercodes fuer fehlgeschlagene Catch-up-Snapshot-Anfragen.
 */
@Serializable
enum class GameStateCatchUpErrorCode {
    GAME_NOT_FOUND,
    NOT_IN_GAME,
    SNAPSHOT_NOT_READY,
    PAYLOAD_TOO_LARGE,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Catch-up-Anfrage.
 *
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable(with = GameStateCatchUpErrorResponseSerializer::class)
data class GameStateCatchUpErrorResponse(
    val code: GameStateCatchUpErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer fuer [GameStateCatchUpErrorResponse].
 */
object GameStateCatchUpErrorResponseSerializer : KSerializer<GameStateCatchUpErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.GameStateCatchUpErrorResponse",
        ) {
            element("code", GameStateCatchUpErrorCode.serializer().descriptor)
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: GameStateCatchUpErrorResponse,
    ) {
        ManualSerializerSupport.encodeStructure(encoder, descriptor) { composite ->
            composite.encodeSerializableElement(
                descriptor,
                0,
                GameStateCatchUpErrorCode.serializer(),
                value.code,
            )
            composite.encodeStringElement(descriptor, 1, value.reason)
        }
    }

    override fun deserialize(decoder: Decoder): GameStateCatchUpErrorResponse =
        ManualSerializerSupport.decodeStructure(decoder, descriptor) { composite ->
            var code: GameStateCatchUpErrorCode? = null
            var reason: String? = null

            loop@ while (true) {
                when (val index = composite.decodeElementIndex(descriptor)) {
                    0 ->
                        code =
                            composite.decodeSerializableElement(
                                descriptor,
                                0,
                                GameStateCatchUpErrorCode.serializer(),
                            )
                    1 -> reason = composite.decodeStringElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break@loop
                    else -> ManualSerializerSupport.unexpectedIndex(index)
                }
            }

            GameStateCatchUpErrorResponse(
                code = code ?: ManualSerializerSupport.missingField("code", descriptor),
                reason = reason ?: ManualSerializerSupport.missingField("reason", descriptor),
            )
        }
}
