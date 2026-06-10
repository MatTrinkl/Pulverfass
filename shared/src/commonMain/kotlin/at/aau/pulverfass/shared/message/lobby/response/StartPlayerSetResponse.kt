package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort auf das Setzen des Startspielers.
 */
@Serializable
data class StartPlayerSetResponse(
    val lobbyCode: LobbyCode,
    val startPlayerId: PlayerId,
) : NetworkMessagePayload

object StartPlayerSetResponseSerializer :
    KSerializer<StartPlayerSetResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        StartPlayerSetResponse.serializer(),
    )
