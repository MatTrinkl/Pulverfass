package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CharacterSelectedBroadcastSerializer::class)
data class CharacterSelectedBroadcast(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val characterId: String,
) : NetworkMessagePayload

@OptIn(ExperimentalSerializationApi::class)
object CharacterSelectedBroadcastSerializer : KSerializer<CharacterSelectedBroadcast> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.message.lobby.event.CharacterSelectedBroadcast",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element<String>("characterId")
        }

    override fun serialize(
        encoder: Encoder,
        value: CharacterSelectedBroadcast,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 0,
            serializer = LobbyCode.serializer(),
            value = value.lobbyCode,
        )
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 1,
            serializer = PlayerId.serializer(),
            value = value.playerId,
        )
        composite.encodeStringElement(descriptor, 2, value.characterId)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): CharacterSelectedBroadcast {
        val composite = decoder.beginStructure(descriptor)
        val serialName = descriptor.serialName
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null
        var characterId: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = decodeLobbyCode(composite)
                1 -> playerId = decodePlayerId(composite)
                2 -> characterId = composite.decodeStringElement(descriptor, 2)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return CharacterSelectedBroadcast(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", serialName),
            playerId = playerId ?: throw MissingFieldException("playerId", serialName),
            characterId = characterId ?: throw MissingFieldException("characterId", serialName),
        )
    }

    private fun decodeLobbyCode(composite: CompositeDecoder): LobbyCode =
        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())

    private fun decodePlayerId(composite: CompositeDecoder): PlayerId =
        composite.decodeSerializableElement(descriptor, 1, PlayerId.serializer())
}
