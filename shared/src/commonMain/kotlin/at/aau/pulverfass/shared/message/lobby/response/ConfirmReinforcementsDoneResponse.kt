package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort auf das Beenden der Verstärkungsphase.
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable
data class ConfirmReinforcementsDoneResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [ConfirmReinforcementsDoneResponse].
 */
object ConfirmReinforcementsDoneResponseSerializer :
    KSerializer<ConfirmReinforcementsDoneResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ConfirmReinforcementsDoneResponse.serializer(),
    )
