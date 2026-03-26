package at.aau.pulverfass.shared.network

enum class MessageType(val id: Int) {
    UNKNOWN_ERROR(0),
    LOGIN_REQUEST(1),
    LOGIN_RESPONSE(2),
    LOGOUT_REQUEST(3),
    CHAT_MESSAGE(4),
    HEARTBEAT(5),
    GAME_JOIN_REQUEST(6),
    GAME_JOIN_RESPONSE(7),
    GAME_CREATE_REQUEST(8),
    GAME_CREATE_RESPONSE(9),
    GAME_ENDED(10);

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: Int): MessageType =
            byId[id] ?: throw IllegalArgumentException("Unknown MessageType id: $id")
    }
}
