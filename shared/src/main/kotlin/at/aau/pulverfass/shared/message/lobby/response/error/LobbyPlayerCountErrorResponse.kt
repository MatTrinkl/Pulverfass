package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene PlayerCount-Anfragen.
 */
@Serializable
enum class LobbyPlayerCountErrorCode {
    LOBBY_NOT_FOUND,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche PlayerCount-Anfrage.
 *
 * @property lobbyCode betroffene Lobby
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable
data class LobbyPlayerCountErrorResponse(
    val lobbyCode: LobbyCode,
    val code: LobbyPlayerCountErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [LobbyPlayerCountErrorResponse].
 */
object LobbyPlayerCountErrorResponseSerializer :
    KSerializer<LobbyPlayerCountErrorResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        LobbyPlayerCountErrorResponse.serializer(),
    )
