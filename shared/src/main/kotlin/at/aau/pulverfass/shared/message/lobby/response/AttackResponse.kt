package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort auf eine verarbeitete [at.aau.pulverfass.shared.message.lobby.request.AttackRequest].
 *
 * @property lobbyCode betroffene Lobby
 * @property requestId optionale Rückgabe der Client-Korrelation aus der Anfrage
 */
@Serializable
data class AttackResponse(
    val lobbyCode: LobbyCode,
    val requestId: String? = null,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [AttackResponse].
 */
object AttackResponseSerializer :
    KSerializer<AttackResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(AttackResponse.serializer())
