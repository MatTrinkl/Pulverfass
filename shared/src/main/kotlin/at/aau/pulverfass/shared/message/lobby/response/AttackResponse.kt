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

@Serializable(with = AttackResponseSerializer::class)
data class AttackResponse(
    val lobbyCode: LobbyCode,
    val requestId: String? = null,
) : NetworkMessagePayload

object AttackResponseSerializer : KSerializer<AttackResponse> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.AttackResponse") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element<String>("requestId", isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: AttackResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        if (value.requestId != null) {
            composite.encodeStringElement(descriptor, 1, value.requestId)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): AttackResponse {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var requestId: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            LobbyCode.serializer(),
                        )
                1 -> requestId = composite.decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return AttackResponse(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            requestId = requestId,
        )
    }
}
