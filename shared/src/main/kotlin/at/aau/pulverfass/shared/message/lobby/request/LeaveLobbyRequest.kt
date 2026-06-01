package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients, eine bestehende Lobby zu verlassen.
 *
 * @property lobbyCode Ziel-Lobby der Leave-Anfrage
 */
@Serializable
data class LeaveLobbyRequest(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [LeaveLobbyRequest].
 */
@OptIn(ExperimentalSerializationApi::class)
object LeaveLobbyRequestSerializer :
    KSerializer<LeaveLobbyRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(LeaveLobbyRequest.serializer())
