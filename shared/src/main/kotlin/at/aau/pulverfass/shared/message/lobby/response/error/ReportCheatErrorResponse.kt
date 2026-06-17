package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.Serializable

/**
 * Typisierte Fehlercodes für fehlgeschlagene Cheat-Meldungen.
 *
 * Diese Fälle sind keine "falschen Verdächtigungen". Eine falsche Verdächtigung
 * ist ein gültiger Report und wird mit [ReportCheatResponse.correct] = false
 * beantwortet. Ein Fehlercode bedeutet dagegen, dass der Report selbst nicht
 * erlaubt oder technisch nicht auswertbar war.
 */
@Serializable
enum class ReportCheatErrorCode {
    /** Die angefragte Lobby existiert serverseitig nicht. */
    GAME_NOT_FOUND,
    /** Die Connection passt nicht zum Reporter im Payload. */
    REQUESTER_MISMATCH,
    /** Vor Spielstart darf noch niemand gemeldet werden. */
    GAME_NOT_RUNNING,
    /** Reporter oder beschuldigter Spieler ist nicht Teil der Lobby. */
    UNKNOWN_PLAYER,
    /** Spieler dürfen sich nicht selbst melden. */
    SELF_REPORT,
    /** Derselbe Spieler hat denselben offenen Cheat bereits gemeldet. */
    ALREADY_REPORTED,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche Cheat-Meldung.
 *
 * Auch hier gilt: `code` ist der stabile technische Teil, `reason` ist die
 * erklärende Nachricht für Logs und UI.
 */
@Serializable
data class ReportCheatErrorResponse(
    val code: ReportCheatErrorCode,
    val reason: String,
) : NetworkMessagePayload
