package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Setzt die Truppenanzahl eines Territoriums auf einen neuen Wert.
 *
 * @property lobbyCode betroffene Lobby
 * @property territoryId betroffenes Territorium
 * @property troopCount neuer absoluter Truppenwert
 * @property stateVersion optionale State-Revision für Delta-Sync zum Client
 */
@Serializable
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
object TerritoryTroopsChangedEventSerializer :
    KSerializer<TerritoryTroopsChangedEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TerritoryTroopsChangedEvent.serializer(),
    )
