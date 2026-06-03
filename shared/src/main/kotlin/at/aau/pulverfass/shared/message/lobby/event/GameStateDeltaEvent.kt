package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.protocol.MessageType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

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

object GameStateDeltaEventSerializer :
    KSerializer<GameStateDeltaEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyTransformingSerializer(
        GameStateDeltaEventWire.serializer(),
        toWire = { value ->
            GameStateDeltaEventWire(
                lobbyCode = value.lobbyCode,
                fromVersion = value.fromVersion,
                toVersion = value.toVersion,
                events = value.events.map(::serializePublicGameEvent),
            )
        },
        fromWire = { wire ->
            GameStateDeltaEvent(
                lobbyCode = wire.lobbyCode,
                fromVersion = wire.fromVersion,
                toVersion = wire.toVersion,
                events = wire.events.map(::deserializePublicGameEvent),
            )
        },
    )

@Serializable
private data class SerializedPublicGameEvent(
    val messageType: MessageType,
    val payload: String,
)

@Serializable
private data class GameStateDeltaEventWire(
    val lobbyCode: LobbyCode,
    val fromVersion: Long,
    val toVersion: Long,
    val events: List<SerializedPublicGameEvent>,
)

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
