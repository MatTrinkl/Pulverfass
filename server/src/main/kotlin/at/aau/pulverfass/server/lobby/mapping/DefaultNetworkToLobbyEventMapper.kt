package at.aau.pulverfass.server.lobby.mapping

import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.network.message.GameJoinRequest
import at.aau.pulverfass.shared.network.message.MessageType

/**
 * Standard-Mapping von dekodierten Netzwerkrequests auf Lobby-Domain-Events.
 *
 * Aktuell unterstützt:
 * - [MessageType.GAME_JOIN_REQUEST] -> [PlayerJoined]
 */
class DefaultNetworkToLobbyEventMapper : NetworkToLobbyEventMapper {
    /**
     * Führt das typspezifische Mapping anhand der Payload-Klasse aus.
     */
    override fun map(request: DecodedNetworkRequest): MappedLobbyEvents =
        when (val payload = request.payload) {
            is GameJoinRequest -> mapGameJoinRequest(request, payload)
            else -> throw UnsupportedLobbyMappingPayloadException(request.header.type)
        }

    /**
     * Mappt einen Join-Request auf ein [PlayerJoined]-Domain-Event.
     *
     * Validiert dabei:
     * 1. Header-Typ passt zum erwarteten Join-Typ
     * 2. PlayerId ist im technischen Kontext vorhanden
     */
    private fun mapGameJoinRequest(
        request: DecodedNetworkRequest,
        payload: GameJoinRequest,
    ): MappedLobbyEvents {
        if (request.header.type != MessageType.GAME_JOIN_REQUEST) {
            throw PayloadHeaderMismatchMappingException(
                messageType = request.header.type,
                payloadTypeName = payload.javaClass.name,
            )
        }

        val playerId =
            request.context.playerId
                ?: throw MissingPlayerContextMappingException(request.header.type)

        val event = PlayerJoined(payload.lobbyCode, playerId)
        return MappedLobbyEvents(
            lobbyCode = payload.lobbyCode,
            events = listOf(event),
            context = request.context,
        )
    }
}
