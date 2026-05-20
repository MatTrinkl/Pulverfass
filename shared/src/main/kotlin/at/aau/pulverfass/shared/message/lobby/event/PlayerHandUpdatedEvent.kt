package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.GameState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PlayerHandUpdatedEventSerializer::class)
data class PlayerHandUpdatedEvent(
    val lobbyCode: LobbyCode,
    override val recipientPlayerId: PlayerId,
    val stateVersion: Long,
    val handCards: List<PrivateHandCardSnapshot>,
) : PrivateGameEvent {
    companion object {
        fun fromGameState(
            gameState: GameState,
            recipientPlayerId: PlayerId,
        ): PlayerHandUpdatedEvent {
            require(gameState.hasPlayer(recipientPlayerId)) {
                "Spieler '${recipientPlayerId.value}' ist nicht Teil der Lobby " +
                    "'${gameState.lobbyCode.value}'."
            }

            return PlayerHandUpdatedEvent(
                lobbyCode = gameState.lobbyCode,
                recipientPlayerId = recipientPlayerId,
                stateVersion = gameState.stateVersion,
                handCards = gameState.handOf(recipientPlayerId).map(PrivateHandCardSnapshot::from),
            )
        }
    }
}

@Serializable(with = PrivateHandCardSnapshotSerializer::class)
data class PrivateHandCardSnapshot(
    val cardId: CardId,
    val type: CardType,
) {
    companion object {
        fun from(card: CardState): PrivateHandCardSnapshot =
            PrivateHandCardSnapshot(cardId = card.cardId, type = card.type)
    }
}

object PlayerHandUpdatedEventSerializer : KSerializer<PlayerHandUpdatedEvent> {
    private val handCardsSerializer = ListSerializer(PrivateHandCardSnapshot.serializer())

    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.PlayerHandUpdatedEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("recipientPlayerId", PlayerId.serializer().descriptor)
            element<Long>("stateVersion")
            element("handCards", handCardsSerializer.descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: PlayerHandUpdatedEvent,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(
            descriptor,
            1,
            PlayerId.serializer(),
            value.recipientPlayerId,
        )
        composite.encodeLongElement(descriptor, 2, value.stateVersion)
        composite.encodeSerializableElement(descriptor, 3, handCardsSerializer, value.handCards)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PlayerHandUpdatedEvent {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var recipientPlayerId: PlayerId? = null
        var stateVersion: Long? = null
        var handCards: List<PrivateHandCardSnapshot>? = null

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
                    recipientPlayerId =
                        composite.decodeSerializableElement(
                            descriptor,
                            1,
                            PlayerId.serializer(),
                        )
                2 -> stateVersion = composite.decodeLongElement(descriptor, 2)
                3 ->
                    handCards =
                        composite.decodeSerializableElement(
                            descriptor,
                            3,
                            handCardsSerializer,
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return PlayerHandUpdatedEvent(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            recipientPlayerId =
                recipientPlayerId
                    ?: throw MissingFieldException("recipientPlayerId", descriptor.serialName),
            stateVersion =
                stateVersion
                    ?: throw MissingFieldException("stateVersion", descriptor.serialName),
            handCards =
                handCards
                    ?: throw MissingFieldException("handCards", descriptor.serialName),
        )
    }
}

object PrivateHandCardSnapshotSerializer : KSerializer<PrivateHandCardSnapshot> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.PrivateHandCardSnapshot",
        ) {
            element("cardId", CardId.serializer().descriptor)
            element("type", CardType.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: PrivateHandCardSnapshot,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, CardId.serializer(), value.cardId)
        composite.encodeSerializableElement(descriptor, 1, CardType.serializer(), value.type)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PrivateHandCardSnapshot {
        val composite = decoder.beginStructure(descriptor)
        var cardId: CardId? = null
        var type: CardType? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    cardId =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            CardId.serializer(),
                        )
                1 ->
                    type =
                        composite.decodeSerializableElement(
                            descriptor,
                            1,
                            CardType.serializer(),
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return PrivateHandCardSnapshot(
            cardId = cardId ?: throw MissingFieldException("cardId", descriptor.serialName),
            type = type ?: throw MissingFieldException("type", descriptor.serialName),
        )
    }
}
