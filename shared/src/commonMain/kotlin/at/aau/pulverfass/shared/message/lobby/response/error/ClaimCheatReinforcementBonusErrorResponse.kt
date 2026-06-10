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
 * Fehlercodes für das Beanspruchen des einmaligen Schummel-Verstärkungsbonus.
 */
@Serializable
enum class ClaimCheatReinforcementBonusErrorCode {
    REQUESTER_MISMATCH,
    NOT_ACTIVE_PLAYER,
    GAME_PAUSED,
    PHASE_MISMATCH,
    GAME_NOT_FOUND,
    ALREADY_USED,
    FORCED_TRADE_REQUIRED,
}

/**
 * Fehlantwort auf das Beanspruchen des einmaligen Schummel-Verstärkungsbonus.
 */
@Serializable(with = ClaimCheatReinforcementBonusErrorResponseSerializer::class)
data class ClaimCheatReinforcementBonusErrorResponse(
    val code: ClaimCheatReinforcementBonusErrorCode,
    val reason: String,
) : NetworkMessagePayload

object ClaimCheatReinforcementBonusErrorResponseSerializer :
    KSerializer<ClaimCheatReinforcementBonusErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.ClaimCheatReinforcementBonusErrorResponse",
        ) {
            element("code", ClaimCheatReinforcementBonusErrorCode.serializer().descriptor)
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: ClaimCheatReinforcementBonusErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor,
            0,
            ClaimCheatReinforcementBonusErrorCode.serializer(),
            value.code,
        )
        composite.encodeStringElement(descriptor, 1, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ClaimCheatReinforcementBonusErrorResponse {
        val composite = decoder.beginStructure(descriptor)
        var code: ClaimCheatReinforcementBonusErrorCode? = null
        var reason: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    code =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            ClaimCheatReinforcementBonusErrorCode.serializer(),
                        )
                1 -> reason = composite.decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return ClaimCheatReinforcementBonusErrorResponse(
            code = code ?: throw MissingFieldException("code", descriptor.serialName),
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
