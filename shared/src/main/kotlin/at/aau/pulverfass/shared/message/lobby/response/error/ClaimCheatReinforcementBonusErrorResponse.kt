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
 *
 * Jeder Code steht für eine fachlich andere Ablehnung. Für die Prüfung ist hier
 * wichtig: Der Server schickt nicht nur freien Text zurück, sondern einen
 * maschinenlesbaren Grund. Die App kann daraus gezielt verständliche
 * Fehlermeldungen bauen.
 */
@Serializable
enum class ClaimCheatReinforcementBonusErrorCode {
    /** Die Connection gehört nicht zu dem Spieler, für den der Request gestellt wurde. */
    REQUESTER_MISMATCH,
    /** Der Spieler ist gerade nicht am Zug. */
    NOT_ACTIVE_PLAYER,
    /** Das Spiel wartet gerade auf einen getrennten Spieler. */
    GAME_PAUSED,
    /** Der Bonus ist nur in der Reinforcements-Phase erlaubt. */
    PHASE_MISMATCH,
    /** Die angefragte Lobby existiert serverseitig nicht. */
    GAME_NOT_FOUND,
    /** Der Spieler hat seinen einmaligen Bonus bereits verbraucht. */
    ALREADY_USED,
    /** Vor weiteren Verstärkungen muss wegen zu vieler Karten ein Trade-In passieren. */
    FORCED_TRADE_REQUIRED,
}

/**
 * Fehlantwort auf das Beanspruchen des einmaligen Schummel-Verstärkungsbonus.
 *
 * `code` ist für Programmlogik und Tests gedacht, `reason` für Logs und UI-nahe
 * Fehlermeldungen. Diese Trennung macht die Tests stabiler, weil sie den Code
 * prüfen können, ohne an jeder Formulierung des Textes zu hängen.
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
