package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Ändert den Besitzer eines Territoriums im GameState.
 *
 * @property lobbyCode betroffene Lobby
 * @property territoryId betroffener Kartenknoten
 * @property ownerId neuer Besitzer oder null, falls das Territorium unbesetzt ist
 * @property stateVersion optionale State-Revision für Delta-Sync zum Client
 */
@Serializable
data class TerritoryOwnerChangedEvent(
    override val lobbyCode: LobbyCode,
    val territoryId: TerritoryId,
    val ownerId: PlayerId? = null,
    val stateVersion: Long? = null,
) : InternalLobbyEvent, PublicGameEvent {
    init {
        require(stateVersion == null || stateVersion >= 0) {
            "TerritoryOwnerChangedEvent.stateVersion darf nicht negativ sein, " +
                "war aber $stateVersion."
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
object TerritoryOwnerChangedEventSerializer :
    KSerializer<TerritoryOwnerChangedEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TerritoryOwnerChangedEvent.serializer(),
    )
