package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
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
 * Setzt die Truppenanzahl eines Territoriums auf einen neuen Wert.
 *
 * @property lobbyCode betroffene Lobby
 * @property territoryId betroffenes Territorium
 * @property troopCount neuer absoluter Truppenwert
 * @property stateVersion optionale State-Revision für Delta-Sync zum Client
 */
@Serializable(with = TerritoryTroopsChangedEventSerializer::class)
data class TerritoryTroopsChangedEvent(
    override val lobbyCode: LobbyCode,
    val territoryId: TerritoryId,
    val troopCount: Int,
    val stateVersion: Long? = null,
) : InternalLobbyEvent, PublicGameEvent {
    init {
        require(troopCount >= 0) {
            "TerritoryTroopsChangedEvent.troopCount darf nicht negativ sein, war aber $troopCount."
        }
        require(stateVersion == null || stateVersion >= 0) {
            "TerritoryTroopsChangedEvent.stateVersion darf nicht negativ sein, " +
                "war aber $stateVersion."
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
object TerritoryTroopsChangedEventSerializer : KSerializer<TerritoryTroopsChangedEvent> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.TerritoryTroopsChangedEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("territoryId", TerritoryId.serializer().descriptor)
            element<Int>("troopCount")
            element<Long>("stateVersion", isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: TerritoryTroopsChangedEvent,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(
            descriptor,
            1,
            TerritoryId.serializer(),
            value.territoryId,
        )
        composite.encodeIntElement(descriptor, 2, value.troopCount)
        if (value.stateVersion != null) {
            composite.encodeLongElement(descriptor, 3, value.stateVersion)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): TerritoryTroopsChangedEvent {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var territoryId: TerritoryId? = null
        var troopCount: Int? = null
        var stateVersion: Long? = null

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
                    territoryId =
                        composite.decodeSerializableElement(
                            descriptor,
                            1,
                            TerritoryId.serializer(),
                        )
                2 -> troopCount = composite.decodeIntElement(descriptor, 2)
                3 -> stateVersion = composite.decodeLongElement(descriptor, 3)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return TerritoryTroopsChangedEvent(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            territoryId =
                territoryId
                    ?: throw MissingFieldException("territoryId", descriptor.serialName),
            troopCount =
                troopCount
                    ?: throw MissingFieldException("troopCount", descriptor.serialName),
            stateVersion = stateVersion,
        )
    }
}
