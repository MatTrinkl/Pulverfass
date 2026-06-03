package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage an den Server, Verstärkungen auf eigene Territorien zu verteilen.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId anfordernder Spieler
 * @property placements gewünschte Verteilung der zu platzierenden Truppen
 */
@Serializable
data class PlaceReinforcementsRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val placements: List<TerritoryPlacement>,
) : NetworkMessagePayload

/**
 * Einzelne Verstärkungsplatzierung innerhalb einer [PlaceReinforcementsRequest].
 *
 * @property territoryId Zielterritorium der Platzierung
 * @property amount Anzahl der dort zu platzierenden Truppen
 */
@Serializable
data class TerritoryPlacement(
    val territoryId: TerritoryId,
    val amount: Int,
) {
    init {
        require(amount > 0) {
            "TerritoryPlacement.amount muss positiv sein, war aber $amount."
        }
    }
}

/**
 * Legacy-Serializer für [PlaceReinforcementsRequest].
 */
object PlaceReinforcementsRequestSerializer :
    KSerializer<PlaceReinforcementsRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PlaceReinforcementsRequest.serializer(),
    )

/**
 * Legacy-Serializer für [TerritoryPlacement].
 */
object TerritoryPlacementSerializer :
    KSerializer<TerritoryPlacement> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TerritoryPlacement.serializer(),
    )
