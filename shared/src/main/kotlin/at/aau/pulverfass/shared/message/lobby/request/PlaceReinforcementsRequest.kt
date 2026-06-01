package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class PlaceReinforcementsRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val placements: List<TerritoryPlacement>,
) : NetworkMessagePayload

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

object PlaceReinforcementsRequestSerializer :
    KSerializer<PlaceReinforcementsRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PlaceReinforcementsRequest.serializer(),
    )

object TerritoryPlacementSerializer :
    KSerializer<TerritoryPlacement> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TerritoryPlacement.serializer(),
    )
