package at.aau.pulverfass.shared.network.message

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
    LOGIN_REQUEST(1),

    /** Antwort des Servers auf eine Login-Anfrage. */
    LOGIN_RESPONSE(2),

    /** Logout-Anfrage eines Clients. */
    LOGOUT_REQUEST(3),

    /** Chat-Nachricht eines Clients an den Server. */
    CHAT_MESSAGE(4),

    /** Broadcastete Chat-Nachricht des Servers. */
    CHAT_MESSAGE_BROADCAST(5),

    /** Anfrage, einem Spiel über einen Lobbycode beizutreten. */
    GAME_JOIN_REQUEST(6),

    /** Antwort auf eine Join-Anfrage. */
    GAME_JOIN_RESPONSE(7),

    /** Anfrage, ein neues Spiel zu erstellen. */
    GAME_CREATE_REQUEST(8),

    /** Antwort auf eine Erstellungsanfrage. */
    GAME_CREATE_RESPONSE(9),

    /** Broadcast, dass ein Spiel beendet wurde. */
    GAME_ENDED_BROADCAST(10),

    /** Broadcast, dass ein Spieler einem Spiel beigetreten ist. */
    GAME_PLAYER_JOINED_BROADCAST(11),

    /** Technische Heartbeat-Nachricht zur Verbindungsüberwachung. */
    HEARTBEAT(12),
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
