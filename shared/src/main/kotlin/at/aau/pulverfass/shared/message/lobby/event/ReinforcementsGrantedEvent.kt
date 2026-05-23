package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ReinforcementsGrantedEventSerializer::class)
data class ReinforcementsGrantedEvent(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val amount: Int,
    val territoryBonus: Int,
    val continentBonus: Int,
    val cardBonus: Int,
) : PublicGameEvent {
    init {
        require(amount >= 0) {
            "ReinforcementsGrantedEvent.amount darf nicht negativ sein, war aber $amount."
        }
        require(territoryBonus >= 0) {
            "ReinforcementsGrantedEvent.territoryBonus darf nicht negativ sein, " +
                "war aber $territoryBonus."
        }
        require(continentBonus >= 0) {
            "ReinforcementsGrantedEvent.continentBonus darf nicht negativ sein, " +
                "war aber $continentBonus."
        }
        require(cardBonus >= 0) {
            "ReinforcementsGrantedEvent.cardBonus darf nicht negativ sein, war aber $cardBonus."
        }
        require(amount == territoryBonus + continentBonus + cardBonus) {
            "ReinforcementsGrantedEvent.amount muss der Summe der Boni entsprechen."
        }
    }
}

object ReinforcementsGrantedEventSerializer : KSerializer<ReinforcementsGrantedEvent> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.ReinforcementsGrantedEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element<Int>("amount")
            element<Int>("territoryBonus")
            element<Int>("continentBonus")
            element<Int>("cardBonus")
        }

    override fun serialize(
        encoder: Encoder,
        value: ReinforcementsGrantedEvent,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.playerId)
        composite.encodeIntElement(descriptor, 2, value.amount)
        composite.encodeIntElement(descriptor, 3, value.territoryBonus)
        composite.encodeIntElement(descriptor, 4, value.continentBonus)
        composite.encodeIntElement(descriptor, 5, value.cardBonus)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ReinforcementsGrantedEvent {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null
        var amount: Int? = null
        var territoryBonus: Int? = null
        var continentBonus: Int? = null
        var cardBonus: Int? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                1 ->
                    playerId =
                        composite.decodeSerializableElement(descriptor, 1, PlayerId.serializer())
                2 -> amount = composite.decodeIntElement(descriptor, 2)
                3 -> territoryBonus = composite.decodeIntElement(descriptor, 3)
                4 -> continentBonus = composite.decodeIntElement(descriptor, 4)
                5 -> cardBonus = composite.decodeIntElement(descriptor, 5)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return ReinforcementsGrantedEvent(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerId =
                playerId
                    ?: throw MissingFieldException("playerId", descriptor.serialName),
            amount = amount ?: throw MissingFieldException("amount", descriptor.serialName),
            territoryBonus =
                territoryBonus
                    ?: throw MissingFieldException("territoryBonus", descriptor.serialName),
            continentBonus =
                continentBonus
                    ?: throw MissingFieldException("continentBonus", descriptor.serialName),
            cardBonus =
                cardBonus ?: throw MissingFieldException("cardBonus", descriptor.serialName),
        )
    }
}
