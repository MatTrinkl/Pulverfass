package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort auf das Beenden der Angriffsphase.
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable
data class ConfirmAttackDoneResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [ConfirmAttackDoneResponse].
 */
object ConfirmAttackDoneResponseSerializer :
    KSerializer<ConfirmAttackDoneResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ConfirmAttackDoneResponse.serializer(),
    )
