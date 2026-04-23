package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.protocol.MessageType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Öffentliches Delta des autoritativen GameStates für eine Lobby.
 *
 * `fromVersion` und `toVersion` beschreiben den Versionsbereich, den dieses
 * Delta abdeckt. Die enthaltenen Events sind in deterministischer Reihenfolge
 * anzuwenden.
 */
@Serializable(with = GameStateDeltaEventSerializer::class)
data class GameStateDeltaEvent(
    val lobbyCode: LobbyCode,
    val fromVersion: Long,
    val toVersion: Long,
    val events: List<PublicGameEvent>,
) : PublicGameStatePayload {
    init {
        require(fromVersion >= 1) {
            "GameStateDeltaEvent.fromVersion darf nicht kleiner als 1 sein, war aber $fromVersion."
        }
        require(toVersion >= fromVersion) {
            "GameStateDeltaEvent.toVersion darf nicht kleiner als " +
                "fromVersion sein, war aber fromVersion=$fromVersion " +
                "toVersion=$toVersion."
        }
        require(events.isNotEmpty()) {
            "GameStateDeltaEvent.events darf nicht leer sein."
        }
    }
}

object GameStateDeltaEventSerializer : KSerializer<GameStateDeltaEvent> {
    private val serializedEventListSerializer =
        ListSerializer(SerializedPublicGameEvent.serializer())

    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.GameStateDeltaEvent") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element<Long>("fromVersion")
            element<Long>("toVersion")
            element("events", serializedEventListSerializer.descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: GameStateDeltaEvent,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeLongElement(descriptor, 1, value.fromVersion)
        composite.encodeLongElement(descriptor, 2, value.toVersion)
        composite.encodeSerializableElement(
            descriptor,
            3,
            serializedEventListSerializer,
            value.events.map(::serializePublicGameEvent),
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): GameStateDeltaEvent {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var fromVersion: Long? = null
        var toVersion: Long? = null
        var events: List<SerializedPublicGameEvent>? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            LobbyCode.serializer(),
                        )
                1 -> fromVersion = composite.decodeLongElement(descriptor, 1)
                2 -> toVersion = composite.decodeLongElement(descriptor, 2)
                3 ->
                    events =
                        composite.decodeSerializableElement(
                            descriptor,
                            3,
                            serializedEventListSerializer,
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return GameStateDeltaEvent(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            fromVersion =
                fromVersion
                    ?: throw MissingFieldException("fromVersion", descriptor.serialName),
            toVersion =
                toVersion
                    ?: throw MissingFieldException("toVersion", descriptor.serialName),
            events =
                events
                    ?.map(::deserializePublicGameEvent)
                    ?: throw MissingFieldException("events", descriptor.serialName),
        )
    }

    private fun serializePublicGameEvent(event: PublicGameEvent): SerializedPublicGameEvent =
        SerializedPublicGameEvent(
            messageType = NetworkPayloadRegistry.messageTypeFor(event),
            payload = NetworkPayloadRegistry.serializePayload(event),
        )

    private fun deserializePublicGameEvent(event: SerializedPublicGameEvent): PublicGameEvent {
        val payload = NetworkPayloadRegistry.deserializePayload(event.messageType, event.payload)
        require(payload is PublicGameEvent) {
            "MessageType '${event.messageType.name}' ist kein " +
                "PublicGameEvent und darf nicht in GameStateDeltaEvent " +
                "verwendet werden."
        }
        return payload
    }
}

@Serializable
private data class SerializedPublicGameEvent(
    val messageType: MessageType,
    val payload: String,
)
