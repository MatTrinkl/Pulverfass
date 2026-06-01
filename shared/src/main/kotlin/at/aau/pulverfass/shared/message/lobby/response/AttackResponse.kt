package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class AttackResponse(
    val lobbyCode: LobbyCode,
    val requestId: String? = null,
) : NetworkMessagePayload

object AttackResponseSerializer :
    KSerializer<AttackResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(AttackResponse.serializer())
