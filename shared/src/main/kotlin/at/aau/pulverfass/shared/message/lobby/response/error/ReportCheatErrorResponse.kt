package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes fuer fehlgeschlagene Cheat-Meldungen.
 */
@Serializable
enum class ReportCheatErrorCode {
    GAME_NOT_FOUND,
    REQUESTER_MISMATCH,
    GAME_NOT_RUNNING,
    UNKNOWN_PLAYER,
    SELF_REPORT,
    ALREADY_REPORTED,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Cheat-Meldung.
 */
@Serializable
data class ReportCheatErrorResponse(
    val code: ReportCheatErrorCode,
    val reason: String,
) : NetworkMessagePayload
