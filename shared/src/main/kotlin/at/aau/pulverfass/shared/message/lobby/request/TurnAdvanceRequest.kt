package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.TurnPhase
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
 * Anfrage eines Clients, den serverseitigen Turn-State deterministisch um einen
 * Schritt weiterzuschalten.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId anfordernder Spieler
 * @property expectedPhase optionale Client-Erwartung zur Synchronisationsprüfung
 */
@Serializable(with = TurnAdvanceRequestSerializer::class)
data class TurnAdvanceRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val expectedPhase: TurnPhase? = null,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [TurnAdvanceRequest].
 */
object TurnAdvanceRequestSerializer : KSerializer<TurnAdvanceRequest> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.TurnAdvanceRequest") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element("expectedPhase", TurnPhase.serializer().descriptor, isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: TurnAdvanceRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.playerId)
        if (value.expectedPhase != null) {
            composite.encodeSerializableElement(descriptor, 2, TurnPhase.serializer(), value.expectedPhase)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): TurnAdvanceRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null
        var expectedPhase: TurnPhase? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                1 -> playerId = composite.decodeSerializableElement(descriptor, 1, PlayerId.serializer())
                2 -> expectedPhase = composite.decodeSerializableElement(descriptor, 2, TurnPhase.serializer())
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return TurnAdvanceRequest(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerId = playerId ?: throw MissingFieldException("playerId", descriptor.serialName),
            expectedPhase = expectedPhase,
        )
    }
}
