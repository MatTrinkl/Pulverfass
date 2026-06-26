package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Verändert den ausstehenden Verstärkungspool eines Spielers um ein Delta.
 *
 * Positive Deltas erhöhen den Pool, negative Deltas verringern ihn.
 */
@Serializable
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
    KSerializer<PendingReinforcementsChangedEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PendingReinforcementsChangedEvent.serializer(),
    )
