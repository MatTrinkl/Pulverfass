package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage an den Server, die Angriffsphase ohne weiteren Angriff abzuschließen.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId anfordernder Spieler
 */
@Serializable
data class ConfirmAttackDoneRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [ConfirmAttackDoneRequest].
 */
object ConfirmAttackDoneRequestSerializer :
    KSerializer<ConfirmAttackDoneRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ConfirmAttackDoneRequest.serializer(),
    )
