package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
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

/**
 * Ändert den Besitzer eines Territoriums im GameState.
 *
 * @property lobbyCode betroffene Lobby
 * @property territoryId betroffener Kartenknoten
 * @property ownerId neuer Besitzer oder null, falls das Territorium unbesetzt ist
 * @property stateVersion optionale State-Revision für Delta-Sync zum Client
 */
@Serializable(with = TerritoryOwnerChangedEventSerializer::class)
data class TerritoryOwnerChangedEvent(
    override val lobbyCode: LobbyCode,
    val territoryId: TerritoryId,
    val ownerId: PlayerId? = null,
    val stateVersion: Long? = null,
) : InternalLobbyEvent, NetworkMessagePayload {
    init {
        require(stateVersion == null || stateVersion >= 0) {
            "TerritoryOwnerChangedEvent.stateVersion darf nicht negativ sein, war aber $stateVersion."
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
object TerritoryOwnerChangedEventSerializer : KSerializer<TerritoryOwnerChangedEvent> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.TerritoryOwnerChangedEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("territoryId", TerritoryId.serializer().descriptor)
            element("ownerId", PlayerId.serializer().descriptor, isOptional = true)
            element<Long>("stateVersion", isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: TerritoryOwnerChangedEvent,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, TerritoryId.serializer(), value.territoryId)
        if (value.ownerId != null) {
            composite.encodeSerializableElement(descriptor, 2, PlayerId.serializer(), value.ownerId)
        }
        if (value.stateVersion != null) {
            composite.encodeLongElement(descriptor, 3, value.stateVersion)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): TerritoryOwnerChangedEvent {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var territoryId: TerritoryId? = null
        var ownerId: PlayerId? = null
        var stateVersion: Long? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                1 -> territoryId = composite.decodeSerializableElement(descriptor, 1, TerritoryId.serializer())
                2 -> ownerId = composite.decodeSerializableElement(descriptor, 2, PlayerId.serializer())
                3 -> stateVersion = composite.decodeLongElement(descriptor, 3)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return TerritoryOwnerChangedEvent(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            territoryId = territoryId ?: throw MissingFieldException("territoryId", descriptor.serialName),
            ownerId = ownerId,
            stateVersion = stateVersion,
        )
    }
}
