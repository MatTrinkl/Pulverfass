package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.MessageType

/**
 * Kontextdaten für Logging und spätere Response-Erzeugung.
 */
data class LobbyRoutingContext(
    /** Quellverbindung des Requests. */
    val connectionId: ConnectionId,
    /** Protokolltyp der eingegangenen Nachricht. */
    val messageType: MessageType,
    /** Ziel-Lobby, falls im Mapping bereits aufgelöst. */
    val lobbyCode: LobbyCode? = null,
)

/**
 * Ergebnis eines einzelnen Routingversuchs.
 */
sealed interface LobbyRoutingResult {
    /**
     * Erfolgreich geroutete Nachricht inklusive Metadaten.
     */
    data class Success(
        val context: LobbyRoutingContext,
        val eventCount: Int,
    ) : LobbyRoutingResult

    /**
     * Fehlgeschlagenes Routing mit typisiertem Fehlerobjekt.
     */
    data class Failure(
        val error: LobbyRoutingError,
    ) : LobbyRoutingResult
}

/**
 * Transportunabhängige Fehlerarten des Router-/Lobby-Layers.
 */
sealed interface LobbyRoutingError {
    val context: LobbyRoutingContext
    val reason: String
    val cause: Throwable?

    data class LobbyNotFound(
        val lobbyCode: LobbyCode,
        override val context: LobbyRoutingContext,
    ) : LobbyRoutingError {
        override val reason: String = "Lobby '${lobbyCode.value}' wurde nicht gefunden."
        override val cause: Throwable? = null
    }

    data class InvalidRoutingData(
        override val reason: String,
        override val context: LobbyRoutingContext,
        override val cause: Throwable? = null,
    ) : LobbyRoutingError

    data class InvalidEvent(
        override val reason: String,
        override val context: LobbyRoutingContext,
        override val cause: Throwable? = null,
    ) : LobbyRoutingError

    data class InvalidStateTransition(
        override val reason: String,
        override val context: LobbyRoutingContext,
        override val cause: Throwable? = null,
    ) : LobbyRoutingError
}
