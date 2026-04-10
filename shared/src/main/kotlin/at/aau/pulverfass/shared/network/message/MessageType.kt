package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.network.UnknownMessageTypeIdException
import kotlinx.serialization.Serializable

/**
 * Definiert welcher Typ in einer Message übertragen wird.
 */
@Serializable
enum class MessageType(val id: Int) {
    /** Wird gesendet, wenn es einen nicht definierten Error oder zustand, gibt. */
    UNKNOWN_ERROR(0),

    /** Der Client sendet einen Login Request zum Server. */
    LOGIN_REQUEST(1),

    /** Der Server sendet eine Response auf ein Login Request zum Client. */
    LOGIN_RESPONSE(2),

    /** Der Client sendet einen Logout Request zum Server. Die Response wird über die Websockets abgewickelt. */
    LOGOUT_REQUEST(3),

    /** Der Client sendet eine Chat-Message an den Server. */
    CHAT_MESSAGE(4),

    /** Der Server broadcasted eine Chat-Message an alle Clients. */
    CHAT_MESSAGE_BROADCAST(5),

    /** Der Client versucht mittels Lobbycode einem Spiel beizutreten. */
    GAME_JOIN_REQUEST(6),

    /** Der Server updated den Client über den versuch einem Spiel beizutreten. */
    GAME_JOIN_RESPONSE(7),

    /** Der Client versucht ein neues Spiel anzulegen. */
    GAME_CREATE_REQUEST(8),

    /** Der Server updated den Client über den Versuch ein neues Spiel anzulegen. */
    GAME_CREATE_RESPONSE(9),

    /** Der Server informiert die Clients das ein Spiel beendet ist. */
    GAME_ENDED_BROADCAST(10),

    /** Der Server informiert alle Clients das ein neuer Spieler dem Spiel beigetreten ist. */
    GAME_PLAYER_JOINED_BROADCAST(11),

    /** Der Client sendet diese Nachricht in einem fix festgelegten Abstand, um dem Server zu zeigen, dass die Connection noch aufrecht ist. */
    HEARTBEAT(12),
    ;

    companion object {
        /**
         * Gibt den MessageType zur gegebenen ID zurück.
         *
         * @param id numerische ID des Nachrichtentyps
         * @return entsprechender MessageType
         * @throws UnknownMessageTypeIdException wenn ID unbekannt ist
         */
        fun fromId(id: Int): MessageType =
            entries.firstOrNull { it.id == id }
                ?: throw UnknownMessageTypeIdException(id)
    }
}
