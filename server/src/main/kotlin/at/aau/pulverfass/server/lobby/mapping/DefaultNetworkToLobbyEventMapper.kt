package at.aau.pulverfass.server.lobby.mapping

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.PlayerKicked
import at.aau.pulverfass.shared.lobby.event.PlayerLeft
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.protocol.MessageType

/**
 * Standard-Mapping von dekodierten Netzwerkrequests auf Lobby-Domain-Events.
 *
 * Aktuell unterstützt:
 * - [MessageType.LOBBY_JOIN_REQUEST] -> [PlayerJoined]
 * - [MessageType.LOBBY_LEAVE_REQUEST] -> [PlayerLeft]
 * - [MessageType.LOBBY_KICK_REQUEST] -> [PlayerKicked]
 * - [MessageType.LOBBY_START_REQUEST] -> [GameStarted]
 */
class DefaultNetworkToLobbyEventMapper : NetworkToLobbyEventMapper {
    /**
     * Führt das typspezifische Mapping anhand der Payload-Klasse aus.
     */
    override fun map(request: DecodedNetworkRequest): MappedLobbyEvents =
        when (val payload = request.payload) {
            is JoinLobbyRequest ->
                mapJoinRequest(
                    request = request,
                    lobbyCode = payload.lobbyCode,
                    playerDisplayName = payload.playerDisplayName,
                    payloadTypeName = payload.javaClass.name,
                )
            is LeaveLobbyRequest ->
                mapLeaveRequest(
                    request = request,
                    lobbyCode = payload.lobbyCode,
                    payloadTypeName = payload.javaClass.name,
                )
            is KickPlayerRequest ->
                mapKickRequest(
                    request = request,
                    lobbyCode = payload.lobbyCode,
                    targetPlayerId = payload.targetPlayerId,
                    payloadTypeName = payload.javaClass.name,
                )
            is StartGameRequest ->
                mapStartGameRequest(
                    request = request,
                    lobbyCode = payload.lobbyCode,
                    payloadTypeName = payload.javaClass.name,
                )
            else -> throw UnsupportedLobbyMappingPayloadException(request.header.type)
        }

    /**
     * Mappt einen Join-Request auf ein [PlayerJoined]-Domain-Event.
     *
     * Validiert dabei:
     * 1. Header-Typ passt zum erwarteten Join-Typ
     * 2. PlayerId ist im technischen Kontext vorhanden
     */
    private fun mapJoinRequest(
        request: DecodedNetworkRequest,
        lobbyCode: LobbyCode,
        playerDisplayName: String,
        payloadTypeName: String,
    ): MappedLobbyEvents {
        if (request.header.type != MessageType.LOBBY_JOIN_REQUEST) {
            throw PayloadHeaderMismatchMappingException(
                messageType = request.header.type,
                payloadTypeName = payloadTypeName,
            )
        }

        val playerId =
            request.context.playerId
                ?: throw MissingPlayerContextMappingException(request.header.type)

        val event = PlayerJoined(lobbyCode, playerId, playerDisplayName)
        return MappedLobbyEvents(
            lobbyCode = lobbyCode,
            events = listOf(event),
            context = request.context,
        )
    }

    /**
     * Mappt einen Leave-Request auf ein [PlayerLeft]-Domain-Event.
     *
     * Validiert dabei:
     * 1. Header-Typ passt zum erwarteten Leave-Typ
     * 2. PlayerId ist im technischen Kontext vorhanden
     */
    private fun mapLeaveRequest(
        request: DecodedNetworkRequest,
        lobbyCode: LobbyCode,
        payloadTypeName: String,
    ): MappedLobbyEvents {
        if (request.header.type != MessageType.LOBBY_LEAVE_REQUEST) {
            throw PayloadHeaderMismatchMappingException(
                messageType = request.header.type,
                payloadTypeName = payloadTypeName,
            )
        }

        val playerId =
            request.context.playerId
                ?: throw MissingPlayerContextMappingException(request.header.type)

        val event = PlayerLeft(lobbyCode, playerId)
        return MappedLobbyEvents(
            lobbyCode = lobbyCode,
            events = listOf(event),
            context = request.context,
        )
    }

    /**
     * Mappt einen Kick-Request auf ein [PlayerKicked]-Domain-Event.
     *
     * Validiert dabei:
     * 1. Header-Typ passt zum erwarteten Kick-Typ
     * 2. PlayerId (Requester) ist im technischen Kontext vorhanden
     */
    private fun mapKickRequest(
        request: DecodedNetworkRequest,
        lobbyCode: LobbyCode,
        targetPlayerId: PlayerId,
        payloadTypeName: String,
    ): MappedLobbyEvents {
        if (request.header.type != MessageType.LOBBY_KICK_REQUEST) {
            throw PayloadHeaderMismatchMappingException(
                messageType = request.header.type,
                payloadTypeName = payloadTypeName,
            )
        }

        val requesterPlayerId =
            request.context.playerId
                ?: throw MissingPlayerContextMappingException(request.header.type)

        val event =
            PlayerKicked(
                lobbyCode = lobbyCode,
                targetPlayerId = targetPlayerId,
                requesterPlayerId = requesterPlayerId,
            )
        return MappedLobbyEvents(
            lobbyCode = lobbyCode,
            events = listOf(event),
            context = request.context,
        )
    }

    /**
     * Mappt einen StartGame-Request auf ein [GameStarted]-Domain-Event.
     *
     * Validiert dabei:
     * 1. Header-Typ passt zum erwarteten Start-Typ
     * 2. PlayerId ist im technischen Kontext vorhanden
     */
    private fun mapStartGameRequest(
        request: DecodedNetworkRequest,
        lobbyCode: LobbyCode,
        payloadTypeName: String,
    ): MappedLobbyEvents {
        if (request.header.type != MessageType.LOBBY_START_REQUEST) {
            throw PayloadHeaderMismatchMappingException(
                messageType = request.header.type,
                payloadTypeName = payloadTypeName,
            )
        }

        request.context.playerId
            ?: throw MissingPlayerContextMappingException(request.header.type)

        val event = GameStarted(lobbyCode)
        return MappedLobbyEvents(
            lobbyCode = lobbyCode,
            events = listOf(event),
            context = request.context,
        )
    }
}
