package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
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
 * Anfrage eines Lobby-Owners, den Startspieler vor Spielstart festzulegen.
 *
 * @property lobbyCode betroffene Lobby
 * @property startPlayerId gewünschter Startspieler
 * @property requesterPlayerId anfordernder Spieler
 */
@Serializable(with = StartPlayerSetRequestSerializer::class)
data class StartPlayerSetRequest(
    val lobbyCode: LobbyCode,
    val startPlayerId: PlayerId,
    val requesterPlayerId: PlayerId,
) : NetworkMessagePayload

object StartPlayerSetRequestSerializer : KSerializer<StartPlayerSetRequest> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.StartPlayerSetRequest") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("startPlayerId", PlayerId.serializer().descriptor)
            element("requesterPlayerId", PlayerId.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: StartPlayerSetRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.startPlayerId)
        composite.encodeSerializableElement(descriptor, 2, PlayerId.serializer(), value.requesterPlayerId)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): StartPlayerSetRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var startPlayerId: PlayerId? = null
        var requesterPlayerId: PlayerId? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                1 -> startPlayerId = composite.decodeSerializableElement(descriptor, 1, PlayerId.serializer())
                2 -> requesterPlayerId = composite.decodeSerializableElement(descriptor, 2, PlayerId.serializer())
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return StartPlayerSetRequest(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            startPlayerId = startPlayerId ?: throw MissingFieldException("startPlayerId", descriptor.serialName),
            requesterPlayerId = requesterPlayerId ?: throw MissingFieldException("requesterPlayerId", descriptor.serialName),
        )
    }
}
