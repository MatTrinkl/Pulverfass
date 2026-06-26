package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
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
 * Erfolgsantwort auf das Beanspruchen des einmaligen Schummel-Verstärkungsbonus.
 *
 * Die Antwort bestätigt nur, dass der Request angenommen wurde. Die sichtbare
 * Spieländerung kommt zusätzlich über die normalen Game-State-Events beim
 * Client an. Dadurch bleibt der Client immer auf dem serverautoritativen
 * Zustand und muss den Bonus nicht selbst in den lokalen State hineinrechnen.
 */
@Serializable(with = ClaimCheatReinforcementBonusResponseSerializer::class)
data class ClaimCheatReinforcementBonusResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

object ClaimCheatReinforcementBonusResponseSerializer :
    KSerializer<ClaimCheatReinforcementBonusResponse> {
    /**
     * Der explizite Serializer hält das Wire-Format der Erfolgsantwort klein
     * und eindeutig: Es wird nur der LobbyCode übertragen, weil der konkrete
     * State über die Event-/Snapshot-Schiene synchronisiert wird.
     */
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.ClaimCheatReinforcementBonusResponse",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: ClaimCheatReinforcementBonusResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ClaimCheatReinforcementBonusResponse {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            LobbyCode.serializer(),
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return ClaimCheatReinforcementBonusResponse(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
        )
    }
}
