package at.aau.pulverfass.shared.lobby.reducer

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Basisklasse für technische Fehler bei der Reduktion eines Lobby-Events.
 */
open class LobbyEventReductionException(
    message: String,
) : RuntimeException(message)

/**
 * Signalisiert, dass ein Event auf einen State einer anderen Lobby angewendet
 * werden sollte.
 */
class LobbyCodeMismatchException(
    expectedLobbyCode: LobbyCode,
    actualLobbyCode: LobbyCode,
) : LobbyEventReductionException(
        "Reducer für Lobby '$expectedLobbyCode' kann kein Event " +
            "für '$actualLobbyCode' verarbeiten.",
    )

/**
 * Signalisiert einen ungültigen Zustandsübergang innerhalb der Lobby-Reduktion.
 */
class InvalidLobbyEventException(
    message: String,
) : LobbyEventReductionException(message)
