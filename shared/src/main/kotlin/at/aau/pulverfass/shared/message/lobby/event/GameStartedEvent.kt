package at.aau.pulverfass.shared.message.lobby.event

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
 * Lobby-Scoped Broadcast des Servers nach dem erfolgreichen Spielstart.
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable(with = GameStartedEventSerializer::class)
data class GameStartedEvent(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

object GameStartedEventSerializer : KSerializer<GameStartedEvent> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.GameStartedEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: GameStartedEvent,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 0,
            serializer = LobbyCode.serializer(),
            value = value.lobbyCode,
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): GameStartedEvent {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = decodeLobbyCode(composite)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return GameStartedEvent(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
        )
    }

    private fun decodeLobbyCode(composite: CompositeDecoder): LobbyCode =
        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
}
