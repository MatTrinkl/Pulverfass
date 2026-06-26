package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort des Servers auf eine Leave-Anfrage.
 *
 * @property lobbyCode Lobby, die der Client verlassen hat
 */
@Serializable
data class LeaveLobbyResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [LeaveLobbyResponse].
 */
@OptIn(ExperimentalSerializationApi::class)
object LeaveLobbyResponseSerializer :
    KSerializer<LeaveLobbyResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        LeaveLobbyResponse.serializer(),
    )
