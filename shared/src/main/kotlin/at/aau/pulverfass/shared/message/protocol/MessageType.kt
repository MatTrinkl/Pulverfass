package at.aau.pulverfass.shared.message.protocol

import at.aau.pulverfass.shared.network.exception.UnknownMessageTypeIdException
import kotlinx.serialization.Serializable

/**
 * Aufzählung aller im Protokoll bekannten Nachrichtentypen.
 *
 * Die numerische [id] ist Teil des stabilen Protokolls und muss eindeutig bleiben.
 */
@Serializable
enum class MessageType(val id: Int) {
    /** Nicht weiter spezifizierter technischer Fehlerfall. */
    UNKNOWN_ERROR(0),

    /** Login-Anfrage eines Clients an den Server. */
    CONNECTION_REQUEST(1),

    /** Antwort des Servers auf eine Login-Anfrage. */
    CONNECTION_RESPONSE(2),

    /** Anfrage zum Wiederverbinden einer bestehenden Session. */
    CONNECTION_RECONNECT_REQUEST(26),

    /** Antwort des Servers auf einen Reconnect-Versuch. */
    CONNECTION_RECONNECT_RESPONSE(27),

    /** Logout-Anfrage eines Clients. */
    LOGOUT_REQUEST(3),

    /** Chat-Nachricht eines Clients an den Server. */
    CHAT_MESSAGE(4),

    /** Broadcastete Chat-Nachricht des Servers. */
    CHAT_MESSAGE_BROADCAST(5),

    /** Anfrage, einem Spiel über einen Lobbycode beizutreten. */
    LOBBY_JOIN_REQUEST(6),

    /** Antwort auf eine Join-Anfrage. */
    LOBBY_JOIN_RESPONSE(7),

    /** Anfrage, ein neues Spiel zu erstellen. */
    LOBBY_CREATE_REQUEST(8),

    /** Antwort auf eine Erstellungsanfrage. */
    LOBBY_CREATE_RESPONSE(9),

    /** Broadcast, dass ein Spiel beendet wurde. */
    LOBBY_ENDED_BROADCAST(10),

    /** Broadcast, dass ein Spieler einem Spiel beigetreten ist. */
    LOBBY_PLAYER_JOINED_BROADCAST(11),

    /** Technische Heartbeat-Nachricht zur Verbindungsüberwachung. */
    HEARTBEAT(12),

    /** Fehlgeschlagene Antwort auf eine Erstellungsanfrage. */
    LOBBY_CREATE_ERROR_RESPONSE(13),

    /** Fehlgeschlagene Antwort auf eine Join-Anfrage. */
    LOBBY_JOIN_ERROR_RESPONSE(14),

    /** Anfrage, eine Lobby zu verlassen. */
    LOBBY_LEAVE_REQUEST(15),

    /** Antwort auf eine Leave-Anfrage. */
    LOBBY_LEAVE_RESPONSE(16),

    /** Broadcast, dass ein Spieler eine Lobby verlassen hat. */
    LOBBY_PLAYER_LEFT_BROADCAST(17),

    /** Anfrage, einen Spieler aus der Lobby zu werfen. */
    LOBBY_KICK_REQUEST(18),

    /** Erfolgreiche Antwort auf eine Kick-Anfrage. */
    LOBBY_KICK_RESPONSE(19),

    /** Fehlantwort auf eine Kick-Anfrage. */
    LOBBY_KICK_ERROR_RESPONSE(20),

    /** Broadcast, dass ein Spieler aus der Lobby geworfen wurde. */
    LOBBY_PLAYER_KICKED_BROADCAST(21),

    /** Anfrage, das Spiel zu starten. */
    LOBBY_START_REQUEST(22),

    /** Antwort auf eine StartGame-Anfrage. */
    LOBBY_START_RESPONSE(23),

    /** Fehlgeschlagene Antwort auf eine StartGame-Anfrage. */
    LOBBY_START_ERROR_RESPONSE(24),

    /** Broadcast, dass das Spiel gestartet wurde. */
    LOBBY_GAME_STARTED_BROADCAST(25),
    ;

    companion object {
        /**
         * Liefert den zu einer numerischen Kennung gehörenden [MessageType].
         *
         * @param id numerische Kennung aus dem Protokoll
         * @return passender [MessageType]
         * @throws UnknownMessageTypeIdException wenn keine Zuordnung existiert
         */
        fun fromId(id: Int): MessageType =
            entries.firstOrNull { it.id == id }
                ?: throw UnknownMessageTypeIdException(id)
    }
}
