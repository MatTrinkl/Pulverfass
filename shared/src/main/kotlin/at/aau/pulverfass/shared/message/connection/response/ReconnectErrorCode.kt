package at.aau.pulverfass.shared.message.connection.response

import kotlinx.serialization.Serializable

/**
 * Standardisierte Fehlercodes für fehlgeschlagene Reconnect-Versuche.
 */
@Serializable
enum class ReconnectErrorCode {
    /** Der übermittelte Session-Token ist unbekannt. */
    TOKEN_INVALID,

    /** Der Session-Token ist zeitlich abgelaufen. */
    TOKEN_EXPIRED,

    /** Der Session-Token wurde serverseitig explizit invalidiert. */
    TOKEN_REVOKED,
}
