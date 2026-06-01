package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Diagnosegrund fuer eine Catch-up-Anfrage nach einem Full Snapshot.
 */
@Serializable
enum class GameStateCatchUpReason {
    MISSING_DELTA,
    OUT_OF_ORDER,
    AFTER_RECONNECT,
}

/**
 * Anfrage eines Clients nach einem vollständigen öffentlichen GameState-Snapshot,
 * wenn lokale Deltas nicht mehr konsistent angewendet werden konnten.
 *
 * @property lobbyCode betroffene Lobby
 * @property clientStateVersion letzte lokal bekannte State-Version des Clients
 * @property reason optionale Diagnoseursache fuer Logging und Analyse
 */
@Serializable
data class GameStateCatchUpRequest(
    val lobbyCode: LobbyCode,
    val clientStateVersion: Long,
    val reason: GameStateCatchUpReason? = null,
) : NetworkMessagePayload

/**
 * Technischer Serializer fuer [GameStateCatchUpRequest].
 */
object GameStateCatchUpRequestSerializer :
    KSerializer<GameStateCatchUpRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        GameStateCatchUpRequest.serializer(),
    )
