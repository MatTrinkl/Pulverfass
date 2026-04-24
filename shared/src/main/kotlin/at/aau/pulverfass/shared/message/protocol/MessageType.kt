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

    /** Anfrage nach einem vollständigen Map-Snapshot. */
    LOBBY_MAP_GET_REQUEST(26),

    /** Erfolgsantwort mit vollständigem Map-Snapshot. */
    LOBBY_MAP_GET_RESPONSE(27),

    /** Fehlantwort auf eine Map-Snapshot-Anfrage. */
    LOBBY_MAP_GET_ERROR_RESPONSE(28),

    /** Delta-Broadcast einer Owner-Änderung auf der Map. */
    LOBBY_TERRITORY_OWNER_CHANGED_BROADCAST(29),

    /** Delta-Broadcast einer Truppenänderung auf der Map. */
    LOBBY_TERRITORY_TROOPS_CHANGED_BROADCAST(30),

    /** Anfrage, den aktuellen Turn-State zum nächsten Schritt fortzuschalten. */
    LOBBY_TURN_ADVANCE_REQUEST(31),

    /** Erfolgreiche Antwort auf eine Turn-Advance-Anfrage. */
    LOBBY_TURN_ADVANCE_RESPONSE(32),

    /** Fehlantwort auf eine Turn-Advance-Anfrage. */
    LOBBY_TURN_ADVANCE_ERROR_RESPONSE(33),

    /** Broadcast eines autoritativ aktualisierten Turn-States. */
    LOBBY_TURN_STATE_UPDATED_BROADCAST(34),

    /** Anfrage nach einem vollständigen Turn-State-Snapshot. */
    LOBBY_TURN_STATE_GET_REQUEST(35),

    /** Erfolgsantwort mit vollständigem Turn-State-Snapshot. */
    LOBBY_TURN_STATE_GET_RESPONSE(36),

    /** Fehlantwort auf eine Turn-State-Snapshot-Anfrage. */
    LOBBY_TURN_STATE_GET_ERROR_RESPONSE(37),

    /** Anfrage, den Startspieler im Lobby-Setup festzulegen. */
    LOBBY_START_PLAYER_SET_REQUEST(38),

    /** Erfolgreiche Antwort auf das Setzen des Startspielers. */
    LOBBY_START_PLAYER_SET_RESPONSE(39),

    /** Fehlantwort auf das Setzen des Startspielers. */
    LOBBY_START_PLAYER_SET_ERROR_RESPONSE(40),

    /** Öffentliches Delta des GameStates für eine Lobby. */
    LOBBY_GAME_STATE_DELTA_BROADCAST(41),

    /** Marker für den Abschluss einer Turn-Phase und den Beginn der nächsten. */
    LOBBY_PHASE_BOUNDARY_BROADCAST(42),

    /** Vollständiger öffentlicher GameState-Snapshot bei Spielerwechsel. */
    LOBBY_GAME_STATE_SNAPSHOT_BROADCAST(43),

    /** Anfrage nach einem privaten, spielerspezifischen GameState-Snapshot. */
    LOBBY_GAME_STATE_PRIVATE_GET_REQUEST(44),

    /** Erfolgsantwort mit privatem, spielerspezifischem GameState-Snapshot. */
    LOBBY_GAME_STATE_PRIVATE_GET_RESPONSE(45),

    /** Fehlantwort auf eine private GameState-Snapshot-Anfrage. */
    LOBBY_GAME_STATE_PRIVATE_GET_ERROR_RESPONSE(46),

    /** Anfrage nach einem vollständigen Catch-up-Snapshot bei erkanntem Desync. */
    LOBBY_GAME_STATE_CATCH_UP_REQUEST(47),

    /** Erfolgsantwort mit vollständigem öffentlichem Catch-up-Snapshot. */
    LOBBY_GAME_STATE_CATCH_UP_RESPONSE(48),

    /** Fehlantwort auf eine Catch-up-Snapshot-Anfrage. */
    LOBBY_GAME_STATE_CATCH_UP_ERROR_RESPONSE(49),
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
