package at.aau.pulverfass.shared.message.lobby.response

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
 * Erfolgsantwort auf das Setzen des Startspielers.
 */
@Serializable(with = StartPlayerSetResponseSerializer::class)
data class StartPlayerSetResponse(
    val lobbyCode: LobbyCode,
    val startPlayerId: PlayerId,
) : NetworkMessagePayload

object StartPlayerSetResponseSerializer : KSerializer<StartPlayerSetResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.StartPlayerSetResponse",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("startPlayerId", PlayerId.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: StartPlayerSetResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(
            descriptor,
            1,
            PlayerId.serializer(),
            value.startPlayerId,
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): StartPlayerSetResponse {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var startPlayerId: PlayerId? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            LobbyCode.serializer(),
                        )
                1 ->
                    startPlayerId =
                        composite.decodeSerializableElement(
                            descriptor,
                            1,
                            PlayerId.serializer(),
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return StartPlayerSetResponse(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            startPlayerId =
                startPlayerId
                    ?: throw MissingFieldException("startPlayerId", descriptor.serialName),
        )
    }
}
