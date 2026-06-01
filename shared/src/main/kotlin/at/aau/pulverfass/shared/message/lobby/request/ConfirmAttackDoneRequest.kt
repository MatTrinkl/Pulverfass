package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class ConfirmAttackDoneRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
) : NetworkMessagePayload

object ConfirmAttackDoneRequestSerializer :
    KSerializer<ConfirmAttackDoneRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ConfirmAttackDoneRequest.serializer(),
    )
