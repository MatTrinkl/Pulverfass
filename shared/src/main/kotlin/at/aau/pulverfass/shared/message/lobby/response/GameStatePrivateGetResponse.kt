package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.message.lobby.event.PrivateGameStatePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Privater Snapshot für genau einen Spieler.
 *
 * Die fachlichen privaten Sammlungen sind aktuell noch Platzhalter für spätere
 * Systeme wie Handkarten oder geheime Ziele. Der Transportpfad ist damit bereits
 * vorhanden, ohne heute schon private Daten zu leaken.
 *
 * @property lobbyCode betroffene Lobby
 * @property recipientPlayerId autorisierter Empfänger dieses Snapshots
 * @property stateVersion öffentliche State-Version, zu der dieser private Snapshot passt
 * @property handCards private Handkarten des Spielers
 * @property secretObjectives private Missions- oder Zielinformationen des Spielers
 */
@Serializable(with = GameStatePrivateGetResponseSerializer::class)
data class GameStatePrivateGetResponse(
    val lobbyCode: LobbyCode,
    override val recipientPlayerId: PlayerId,
    val stateVersion: Long,
    val handCards: List<String> = emptyList(),
    val secretObjectives: List<String> = emptyList(),
) : PrivateGameStatePayload {
    companion object {
        fun fromGameState(
            gameState: GameState,
            recipientPlayerId: PlayerId,
        ): GameStatePrivateGetResponse = gameState.toGameStatePrivateGetResponse(recipientPlayerId)
    }
}

/**
 * Technischer Serializer für [GameStatePrivateGetResponse].
 */
object GameStatePrivateGetResponseSerializer : KSerializer<GameStatePrivateGetResponse> {
    private val stringListSerializer = ListSerializer(String.serializer())

    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.GameStatePrivateGetResponse") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("recipientPlayerId", PlayerId.serializer().descriptor)
            element<Long>("stateVersion")
            element("handCards", stringListSerializer.descriptor)
            element("secretObjectives", stringListSerializer.descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: GameStatePrivateGetResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.recipientPlayerId)
        composite.encodeLongElement(descriptor, 2, value.stateVersion)
        composite.encodeSerializableElement(descriptor, 3, stringListSerializer, value.handCards)
        composite.encodeSerializableElement(descriptor, 4, stringListSerializer, value.secretObjectives)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): GameStatePrivateGetResponse {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var recipientPlayerId: PlayerId? = null
        var stateVersion: Long? = null
        var handCards: List<String>? = null
        var secretObjectives: List<String>? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                1 ->
                    recipientPlayerId =
                        composite.decodeSerializableElement(descriptor, 1, PlayerId.serializer())
                2 -> stateVersion = composite.decodeLongElement(descriptor, 2)
                3 -> handCards = composite.decodeSerializableElement(descriptor, 3, stringListSerializer)
                4 ->
                    secretObjectives =
                        composite.decodeSerializableElement(descriptor, 4, stringListSerializer)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return GameStatePrivateGetResponse(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            recipientPlayerId =
                recipientPlayerId ?: throw MissingFieldException("recipientPlayerId", descriptor.serialName),
            stateVersion = stateVersion ?: throw MissingFieldException("stateVersion", descriptor.serialName),
            handCards = handCards ?: throw MissingFieldException("handCards", descriptor.serialName),
            secretObjectives =
                secretObjectives ?: throw MissingFieldException("secretObjectives", descriptor.serialName),
        )
    }
}

internal fun GameState.toGameStatePrivateGetResponse(recipientPlayerId: PlayerId): GameStatePrivateGetResponse {
    require(hasPlayer(recipientPlayerId)) {
        "Spieler '${recipientPlayerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
    }

    return GameStatePrivateGetResponse(
        lobbyCode = lobbyCode,
        recipientPlayerId = recipientPlayerId,
        stateVersion = stateVersion,
        handCards = emptyList(),
        secretObjectives = emptyList(),
    )
}
