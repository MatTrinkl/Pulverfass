package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
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

@Serializable(with = CharacterSelectResponseSerializer::class)
data class CharacterSelectResponse(
    val lobbyCode: LobbyCode,
    val characterId: String,
) : NetworkMessagePayload

@OptIn(ExperimentalSerializationApi::class)
object CharacterSelectResponseSerializer : KSerializer<CharacterSelectResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.message.lobby.response.CharacterSelectResponse",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element<String>("characterId")
        }

    override fun serialize(
        encoder: Encoder,
        value: CharacterSelectResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 0,
            serializer = LobbyCode.serializer(),
            value = value.lobbyCode,
        )
        composite.encodeStringElement(descriptor, 1, value.characterId)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): CharacterSelectResponse {
        val composite = decoder.beginStructure(descriptor)
        val serialName = descriptor.serialName
        var lobbyCode: LobbyCode? = null
        var characterId: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = decodeLobbyCode(composite)
                1 -> characterId = composite.decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return CharacterSelectResponse(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", serialName),
            characterId = characterId ?: throw MissingFieldException("characterId", serialName),
        )
    }

    private fun decodeLobbyCode(composite: CompositeDecoder): LobbyCode =
        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
}
