package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients, den serverseitigen Turn-State deterministisch um einen
 * Schritt weiterzuschalten.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId anfordernder Spieler
 * @property expectedPhase optionale Client-Erwartung zur Synchronisationsprüfung
 */
@Serializable
data class TurnAdvanceRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val expectedPhase: TurnPhase? = null,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [TurnAdvanceRequest].
 */
object TurnAdvanceRequestSerializer :
    KSerializer<TurnAdvanceRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TurnAdvanceRequest.serializer(),
    )
