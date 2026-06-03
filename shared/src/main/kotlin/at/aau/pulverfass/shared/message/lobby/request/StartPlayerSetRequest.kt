package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Lobby-Owners, den Startspieler vor Spielstart festzulegen.
 *
 * @property lobbyCode betroffene Lobby
 * @property startPlayerId gewünschter Startspieler
 * @property requesterPlayerId anfordernder Spieler
 */
@Serializable
data class StartPlayerSetRequest(
    val lobbyCode: LobbyCode,
    val startPlayerId: PlayerId,
    val requesterPlayerId: PlayerId,
) : NetworkMessagePayload

object StartPlayerSetRequestSerializer :
    KSerializer<StartPlayerSetRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        StartPlayerSetRequest.serializer(),
    )
