package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Basistyp für technische Fehler im Main-Server-Routing.
 */
sealed class MainServerRoutingException(
    message: String,
) : IllegalArgumentException(message)

/**
 * Signalisiert, dass für einen gerouteten Request keine passende Lobby-Instanz existiert.
 */
class UnknownLobbyRoutingException(
    lobbyCode: LobbyCode,
) : MainServerRoutingException(
        "Unknown lobby for routed request: '${lobbyCode.value}'.",
    )

/**
 * Signalisiert, dass das Mapping keine Domain-Events erzeugt hat.
 */
class EmptyMappedEventsRoutingException(
    lobbyCode: LobbyCode,
) : MainServerRoutingException(
        "Mapped request for lobby '${lobbyCode.value}' did not produce any events.",
    )

/**
 * Signalisiert, dass gemappte Events einer anderen Lobby zugeordnet sind.
 */
class RoutedLobbyMismatchException(
    expectedLobbyCode: LobbyCode,
    actualLobbyCode: LobbyCode,
) : MainServerRoutingException(
        "Mapped event lobby '${actualLobbyCode.value}' does not match " +
            "routed lobby '${expectedLobbyCode.value}'.",
    )

/**
 * Signalisiert ein ungültiges Domain-Event für die Ziel-Lobby.
 */
class InvalidRoutedEventException(
    message: String,
) : MainServerRoutingException(message)

/**
 * Signalisiert inkonsistente oder nicht verarbeitbare Routingdaten.
 */
class InvalidRoutingDataRoutingException(
    message: String,
) : MainServerRoutingException(message)

/**
 * Signalisiert einen ungültigen Zustandsübergang im Lobby-Layer.
 */
class InvalidStateTransitionRoutingException(
    message: String,
) : MainServerRoutingException(message)
