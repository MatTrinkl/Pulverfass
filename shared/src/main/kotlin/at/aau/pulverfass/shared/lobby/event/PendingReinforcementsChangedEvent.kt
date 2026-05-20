package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Verändert den ausstehenden Verstärkungspool eines Spielers um ein Delta.
 *
 * Positive Deltas erhöhen den Pool, negative Deltas verringern ihn.
 */
@Serializable(with = PendingReinforcementsChangedEventSerializer::class)
data class PendingReinforcementsChangedEvent(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val delta: Int,
) : InternalLobbyEvent, PublicGameEvent {
    init {
        require(delta != 0) {
            "PendingReinforcementsChangedEvent.delta darf nicht 0 sein."
        }
    }
}

object PendingReinforcementsChangedEventSerializer :
    KSerializer<PendingReinforcementsChangedEvent> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.PendingReinforcementsChangedEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element<Int>("delta")
        }

    override fun serialize(
        encoder: Encoder,
        value: PendingReinforcementsChangedEvent,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.playerId)
        composite.encodeIntElement(descriptor, 2, value.delta)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PendingReinforcementsChangedEvent {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null
        var delta: Int? = null

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
                    playerId =
                        composite.decodeSerializableElement(
                            descriptor,
                            1,
                            PlayerId.serializer(),
                        )
                2 -> delta = composite.decodeIntElement(descriptor, 2)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return PendingReinforcementsChangedEvent(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerId = playerId ?: throw MissingFieldException("playerId", descriptor.serialName),
            delta = delta ?: throw MissingFieldException("delta", descriptor.serialName),
        )
    }
}
