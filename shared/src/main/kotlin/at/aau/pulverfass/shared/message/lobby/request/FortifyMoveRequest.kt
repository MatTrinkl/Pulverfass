package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class FortifyMoveRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val fromTerritoryId: TerritoryId,
    val toTerritoryId: TerritoryId,
    val troopCount: Int,
) : NetworkMessagePayload {
    init {
        require(troopCount > 0) {
            "FortifyMoveRequest.troopCount muss positiv sein, war aber $troopCount."
        }
        require(fromTerritoryId != toTerritoryId) {
            "FortifyMoveRequest benötigt unterschiedliche Territorien."
        }
    }
}

object FortifyMoveRequestSerializer :
    KSerializer<FortifyMoveRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        FortifyMoveRequest.serializer(),
    )
