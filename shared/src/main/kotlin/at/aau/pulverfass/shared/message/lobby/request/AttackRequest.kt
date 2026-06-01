package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.command.MIN_ATTACK_COMMITTED_TROOPS
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class AttackRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val fromTerritoryId: TerritoryId,
    val toTerritoryId: TerritoryId,
    val attackTroops: Int,
    val moveAfterCapture: Int,
    val requestId: String? = null,
) : NetworkMessagePayload {
    init {
        require(attackTroops >= MIN_ATTACK_COMMITTED_TROOPS) {
            "AttackRequest.attackTroops muss mindestens $MIN_ATTACK_COMMITTED_TROOPS sein, " +
                "war aber $attackTroops."
        }
        require(moveAfterCapture > 0) {
            "AttackRequest.moveAfterCapture muss positiv sein, war aber $moveAfterCapture."
        }
        require(requestId == null || requestId.isNotBlank()) {
            "AttackRequest.requestId darf nicht leer sein."
        }
    }
}

object AttackRequestSerializer :
    KSerializer<AttackRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(AttackRequest.serializer())
