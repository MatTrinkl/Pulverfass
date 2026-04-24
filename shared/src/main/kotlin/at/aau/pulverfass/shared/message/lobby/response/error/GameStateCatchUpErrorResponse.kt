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
 * Typisierte Fehlercodes fuer fehlgeschlagene Catch-up-Snapshot-Anfragen.
 */
@Serializable
enum class GameStateCatchUpErrorCode {
    GAME_NOT_FOUND,
    NOT_IN_GAME,
    SNAPSHOT_NOT_READY,
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
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor,
            0,
            GameStateCatchUpErrorCode.serializer(),
            value.code,
        )
        composite.encodeStringElement(descriptor, 1, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): GameStateCatchUpErrorResponse {
        val composite = decoder.beginStructure(descriptor)
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
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return GameStateCatchUpErrorResponse(
            code = code ?: throw MissingFieldException("code", descriptor.serialName),
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
