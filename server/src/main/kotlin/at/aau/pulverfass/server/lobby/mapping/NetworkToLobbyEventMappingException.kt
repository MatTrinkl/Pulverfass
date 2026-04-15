package at.aau.pulverfass.server.lobby.mapping

import at.aau.pulverfass.shared.message.protocol.MessageType

/**
 * Basistyp für technische Mapping-Fehler zwischen Netzwerkmodell und Lobby-Domain.
 */
sealed class NetworkToLobbyEventMappingException(
    message: String,
) : IllegalArgumentException(message)

class UnsupportedLobbyMappingPayloadException(
    messageType: MessageType,
) : NetworkToLobbyEventMappingException(
        "Message type '$messageType' ist für Lobby-Mapping aktuell nicht unterstützt.",
    )

/**
 * Signalisiert, dass Header-Typ und Payload-Klasse nicht zusammenpassen.
 */
class PayloadHeaderMismatchMappingException(
    messageType: MessageType,
    payloadTypeName: String,
) : NetworkToLobbyEventMappingException(
        "Payload '$payloadTypeName' passt nicht zu Header '$messageType'.",
    )

/**
 * Signalisiert, dass für das Mapping eine Player-Zuordnung fehlt.
 */
class MissingPlayerContextMappingException(
    messageType: MessageType,
) : NetworkToLobbyEventMappingException(
        "Für '$messageType' wird EventContext.playerId benötigt.",
    )
