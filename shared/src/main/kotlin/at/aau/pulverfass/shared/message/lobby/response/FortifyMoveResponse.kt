package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort auf einen verarbeiteten Fortify-Zug.
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable
data class FortifyMoveResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [FortifyMoveResponse].
 */
object FortifyMoveResponseSerializer :
    KSerializer<FortifyMoveResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        FortifyMoveResponse.serializer(),
    )
